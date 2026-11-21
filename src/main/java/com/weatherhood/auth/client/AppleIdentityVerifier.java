package com.weatherhood.auth.client;

import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Apple 공개키를 캐시하면서 서명·발급자·앱·필수 claim을 검증한다. nonce 비교는 로그인 서비스가 담당한다.
 */
@Component
public class AppleIdentityVerifier {
    private final NimbusJwtDecoder decoder;
    private final String clientId;
    private final Clock clock;

    /**
     * 의존 객체와 외부 설정을 받아 AppleIdentityVerifier를 구성한다.
     *
     * @param jwkUri   Apple 공개키 목록 주소
     * @param clientId 허용할 native iOS 앱 Bundle ID
     * @param clock    만료 및 발표 시각 판정에 사용할 시계
     */
    public AppleIdentityVerifier(@Value("${auth.apple.jwk-set-uri:https://appleid.apple.com/auth/keys}") String jwkUri,
                                 @Value("${auth.apple.client-id:co.kr.ansandy.weatherhood}") String clientId,
                                 Clock clock) {
        this.clientId = clientId;
        this.clock = clock;
        var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        decoder = NimbusJwtDecoder.withJwkSetUri(jwkUri).restOperations(new RestTemplate(factory)).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator("https://appleid.apple.com")));
    }

    /**
     * 잘못된 자격 증명은 401, 공개키 통신 실패는 503 업무 오류로 변환한다.
     *
     * @param identityToken Apple이 서명한 사용자 식별 JWT
     * @return 서명과 필수 claim을 검증한 JWT
     */
    public Jwt verifyIdentity(String identityToken) {
        try {
            Jwt jwt = decoder.decode(identityToken);
            if (jwt.getAudience() == null || jwt.getAudience().size() != 1 || !jwt.getAudience().contains(clientId)
                    || jwt.getSubject() == null || jwt.getSubject().isBlank() || jwt.getSubject().length() > 255
                    || jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                    || !jwt.getExpiresAt().isAfter(clock.instant())
                    || !jwt.getIssuedAt().isBefore(jwt.getExpiresAt())
                    || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(60))) {
                throw new ApplicationException(ErrorCode.INVALID_APPLE_CREDENTIAL);
            }
            return jwt;
        } catch (BadJwtException exception) {
            throw new ApplicationException(ErrorCode.INVALID_APPLE_CREDENTIAL);
        } catch (JwtException exception) {
            throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
        }
    }
}
