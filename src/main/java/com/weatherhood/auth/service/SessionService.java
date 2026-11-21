package com.weatherhood.auth.service;

import com.weatherhood.auth.client.AppleAuthClient;
import com.weatherhood.auth.client.AppleIdentityVerifier;
import com.weatherhood.auth.dto.SessionIdentity;
import com.weatherhood.auth.entity.UserAccount;
import com.weatherhood.auth.repository.UserAccountRepository;
import com.weatherhood.auth.repository.UserSessionRepository;
import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 해시 기반 세션 인증, 현재 기기 로그아웃 및 Apple 폐기를 동반한 탈퇴를 담당한다.
 */
@Service
public class SessionService {
    private final UserSessionRepository sessions;
    private final UserAccountRepository users;
    private final AppleAuthClient apple;
    private final AppleIdentityVerifier verifier;
    private final TokenCipher cipher;
    private final Clock clock;

    /**
     * 의존 객체와 외부 설정을 받아 SessionService를 구성한다.
     *
     * @param sessions 세션 저장 또는 인증 서비스
     * @param users    내부 사용자 계정 저장소
     * @param apple    Apple 토큰 교환·검증·폐기 클라이언트
     * @param verifier Apple 서명·claim 검증기
     * @param cipher   Apple refresh token 암호화기
     * @param clock    만료 및 발표 시각 판정에 사용할 시계
     */
    public SessionService(UserSessionRepository sessions, UserAccountRepository users, AppleAuthClient apple,
                          AppleIdentityVerifier verifier, TokenCipher cipher, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.apple = apple;
        this.verifier = verifier;
        this.cipher = cipher;
        this.clock = clock;
    }

    /**
     * Apple 연결 해제를 감지한 세션 삭제는 401을 반환하더라도 커밋한다.
     *
     * @param token 로그에 기록하지 않을 토큰 원문
     * @return 세션 소유자와 폐기용 해시
     */
    @Transactional(noRollbackFor = ApplicationException.class)
    public SessionIdentity authenticateToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED);
        }
        String hash = SecretTokens.hashToken(token);
        var session = sessions.findById(hash).filter(value -> value.expiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        UserAccount user = users.findLockedById(session.userId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        // 사용자 잠금을 기다리는 동안 로그아웃이나 탈퇴한 경우 다시 확인한다.
        if (!sessions.existsById(hash) || !session.expiresAt().isAfter(clock.instant())) {
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED);
        }
        validateAppleSessionIfDue(user);
        return new SessionIdentity(user.id(), hash);
    }

    /**
     * 요청한 세션만 종료하고 다른 기기의 세션은 유지한다.
     *
     * @param identity 인증 필터에서 검증한 현재 사용자와 세션
     */
    @Transactional
    public void logoutSession(SessionIdentity identity) {
        users.findLockedById(identity.userId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        sessions.deleteById(identity.tokenHash());
    }

    /**
     * Apple 폐기에 실패하면 로컬 계정은 보존해 탈퇴 재시도를 허용한다.
     *
     * @param identity 인증 필터에서 검증한 현재 사용자와 세션
     */
    @Transactional
    public void deleteAccount(SessionIdentity identity) {
        var user = users.findLockedById(identity.userId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        apple.revokeRefreshToken(cipher.decryptToken(user.refreshTokenCiphertext()));
        users.delete(user);
    }

    private void validateAppleSessionIfDue(UserAccount user) {
        if (user.appleValidatedAt().plus(Duration.ofDays(1)).isAfter(clock.instant())) {
            return;
        }
        try {
            String token = apple.validateRefreshToken(cipher.decryptToken(user.refreshTokenCiphertext()));
            var identity = verifier.verifyIdentity(token);
            if (!user.appleSubject().equals(identity.getSubject())) {
                throw new ApplicationException(ErrorCode.INVALID_APPLE_CREDENTIAL);
            }
            user.updateAppleValidationTime(clock.instant());
        } catch (ApplicationException exception) {
            if (exception.code() != ErrorCode.INVALID_APPLE_CREDENTIAL) {
                throw exception;
            }
            sessions.deleteByUserId(user.id());
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
