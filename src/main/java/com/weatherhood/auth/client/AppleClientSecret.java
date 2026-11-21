package com.weatherhood.auth.client;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Git 외부 PKCS#8 파일을 사용해 Apple 서버 요청용 5분짜리 ES256 JWT를 만든다.
 */
@Component
public class AppleClientSecret {
    private final String teamId;
    private final String keyId;
    private final String clientId;
    private final String keyPath;
    private final Clock clock;

    /**
     * 의존 객체와 외부 설정을 받아 AppleClientSecret를 구성한다.
     *
     * @param teamId   Apple Developer 팀 식별자
     * @param keyId    Apple 서버 인증 키 식별자
     * @param clientId 허용할 native iOS 앱 Bundle ID
     * @param keyPath  Git 외부 PKCS#8 .p8 파일의 절대 경로
     * @param clock    만료 및 발표 시각 판정에 사용할 시계
     */
    public AppleClientSecret(@Value("${auth.apple.team-id:}") String teamId,
                             @Value("${auth.apple.key-id:}") String keyId,
                             @Value("${auth.apple.client-id:co.kr.ansandy.weatherhood}") String clientId,
                             @Value("${auth.apple.private-key-path:}") String keyPath, Clock clock) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.clientId = clientId;
        this.keyPath = keyPath;
        this.clock = clock;
    }

    /**
     * 현재 시각부터 5분 동안 유효한 ES256 client_secret을 서명한다.
     *
     * @return 서명한 5분 유효 client_secret JWT
     */
    public String createClientSecret() {
        if (teamId.isBlank() || keyId.isBlank() || keyPath.isBlank()) {
            throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
        }
        try {
            String pem = Files.readString(Path.of(keyPath)).replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            var key = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            var now = clock.instant();
            var claims = new JWTClaimsSet.Builder().issuer(teamId).subject(clientId)
                    .audience("https://appleid.apple.com").issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(300))).build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims);
            jwt.sign(new ECDSASigner(key));
            return jwt.serialize();
        } catch (IOException | GeneralSecurityException | JOSEException | IllegalArgumentException exception) {
            throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
        }
    }
}
