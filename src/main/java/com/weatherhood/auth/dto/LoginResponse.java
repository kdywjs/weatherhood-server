package com.weatherhood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 내부 세션의 최초 발급 응답.
 *
 * @param accessToken 최초 발급 시에만 반환하는 서버 세션 토큰
 * @param tokenType   인증 헤더 방식 Bearer
 * @param expiresAt   UTC 기준 절대 만료 시각
 * @param user        로그인한 내부 사용자 식별 정보
 */
public record LoginResponse(
       
        @Schema(description = "개인 API의 Bearer 헤더에 사용할 서버 세션 토큰")
        String accessToken,
        
        @Schema(description = "헤더 인증 방식", example = "Bearer")
        String tokenType,
        
        @Schema(description = "UTC ISO 8601 절대 만료 시각", example = "2026-12-21T03:00:00Z")
        Instant expiresAt,
        
        @Schema(description = "내부 사용자 정보") UserResponse user)
        {

    @Override
    public String toString() {
        return "LoginResponse[redacted]";
    }
}
