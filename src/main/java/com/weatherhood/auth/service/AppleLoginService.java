package com.weatherhood.auth.service;

import com.weatherhood.auth.client.AppleAuthClient;
import com.weatherhood.auth.client.AppleIdentityVerifier;
import com.weatherhood.auth.dto.ChallengeGrant;
import com.weatherhood.auth.dto.LoginGrant;
import com.weatherhood.auth.entity.LoginChallenge;
import com.weatherhood.auth.entity.UserSession;
import com.weatherhood.auth.repository.LoginChallengeRepository;
import com.weatherhood.auth.repository.UserAccountRepository;
import com.weatherhood.auth.repository.UserSessionRepository;
import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apple 자격 검증과 일회용 challenge 소비를 거쳐 내부 사용자와 세션을 생성한다.
 */
@Service
public class AppleLoginService {
    private final LoginChallengeRepository challenges;
    private final UserAccountRepository users;
    private final UserSessionRepository sessions;
    private final AppleIdentityVerifier verifier;
    private final AppleAuthClient apple;
    private final TokenCipher cipher;
    private final Clock clock;

    /**
     * 의존 객체와 외부 설정을 받아 AppleLoginService를 구성한다.
     *
     * @param challenges 일회용 로그인 challenge 저장소
     * @param users      내부 사용자 계정 저장소
     * @param sessions   세션 저장 또는 인증 서비스
     * @param verifier   Apple 서명·claim 검증기
     * @param apple      Apple 토큰 교환·검증·폐기 클라이언트
     * @param cipher     Apple refresh token 암호화기
     * @param clock      만료 및 발표 시각 판정에 사용할 시계
     */
    public AppleLoginService(LoginChallengeRepository challenges, UserAccountRepository users,
                             UserSessionRepository sessions, AppleIdentityVerifier verifier, AppleAuthClient apple,
                             TokenCipher cipher, Clock clock) {
        this.challenges = challenges;
        this.users = users;
        this.sessions = sessions;
        this.verifier = verifier;
        this.apple = apple;
        this.cipher = cipher;
        this.clock = clock;
    }

    /**
     * 5분간 유효한 일회용 challenge를 발급하고 nonce 해시만 저장한다.
     *
     * @return 일회용 nonce·ID·만료 시각
     */
    @Transactional
    public ChallengeGrant createChallenge() {
        var id = UUID.randomUUID();
        String nonce = SecretTokens.generateToken();
        var expiresAt = clock.instant().plusSeconds(300);
        challenges.save(new LoginChallenge(id, SecretTokens.hashToken(nonce), expiresAt));
        return new ChallengeGrant(id, nonce, expiresAt);
    }

    /**
     * 코드 교환까지 성공해야 세션을 발급한다. DB 경합에서 challenge 소비는 한 요청만 성공한다.
     *
     * @param challengeId       서버가 발급한 일회용 challenge ID
     * @param identityToken     Apple이 서명한 사용자 식별 JWT
     * @param authorizationCode Apple에서 받은 일회용 인증 코드
     * @return 서버 세션 원문·만료 시각·사용자 ID
     */
    @Transactional
    public LoginGrant loginWithApple(UUID challengeId, String identityToken, String authorizationCode) {
        LoginChallenge challenge = challenges.findById(challengeId)
                .filter(value -> value.expiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_APPLE_CREDENTIAL));
        var identity = verifier.verifyIdentity(identityToken);
        if (!hasMatchingNonce(identity, challenge.nonceHash())) {
            throw new ApplicationException(ErrorCode.INVALID_APPLE_CREDENTIAL);
        }
        var tokens = apple.exchangeAuthorizationCode(authorizationCode);
        var exchanged = verifier.verifyIdentity(tokens.identityToken());
        if (!identity.getSubject().equals(exchanged.getSubject())
                || !hasMatchingNonce(exchanged, challenge.nonceHash())) {
            throw new ApplicationException(ErrorCode.INVALID_APPLE_CREDENTIAL);
        }
        if (challenges.consumeChallenge(challengeId, clock.instant()) != 1) {
            throw new ApplicationException(ErrorCode.INVALID_APPLE_CREDENTIAL);
        }
        String encryptedRefreshToken = cipher.encryptToken(tokens.refreshToken());
        users.insertAccountIfAbsent(UUID.randomUUID(), identity.getSubject(), encryptedRefreshToken, clock.instant());
        var user = users.findLockedByAppleSubject(identity.getSubject()).orElseThrow();
        user.updateAppleCredential(encryptedRefreshToken, clock.instant());
        String token = SecretTokens.generateToken();
        var expiresAt = clock.instant().plus(Duration.ofDays(30));
        sessions.save(new UserSession(SecretTokens.hashToken(token), user.id(), expiresAt));
        return new LoginGrant(token, expiresAt, user.id());
    }

    private boolean hasMatchingNonce(Jwt identity, String expectedHash) {
        return identity.getClaims().get("nonce") instanceof String nonce && SecretTokens.matchesHash(nonce,
                expectedHash);
    }
}
