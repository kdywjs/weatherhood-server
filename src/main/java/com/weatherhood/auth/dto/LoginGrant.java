package com.weatherhood.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 로그인 서비스 결과이며 토큰 원문은 HTTP 발급 응답 이외에 기록하지 않는다.
 *
 * @param token     로그에 기록하지 않을 토큰 원문
 * @param expiresAt UTC 기준 절대 만료 시각
 * @param userId    인증된 사용자의 내부 UUID
 */
public record LoginGrant(String token, Instant expiresAt, UUID userId) {
    @Override
    public String toString() {
        return "LoginGrant[redacted]";
    }
}
