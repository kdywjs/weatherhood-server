package com.weatherhood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Apple 요청에 그대로 전달할 nonce와 challenge 만료 시각.
 *
 * @param challengeId 서버가 발급한 일회용 challenge ID
 * @param nonce       Apple 인증 요청에 변형 없이 전달할 무작위 값
 * @param expiresAt   UTC 기준 절대 만료 시각
 */
public record ChallengeResponse(
        @Schema(description = "이 로그인 시도를 식별하는 UUID")
        UUID challengeId,
        @Schema(description = "Apple 로그인 요청에 그대로 전달할 값")
        String nonce,
        @Schema(description = "UTC ISO 8601 만료 시각", example = "2026-11-21T03:05:00Z") Instant expiresAt)
        {

    @Override
    public String toString() {
        return "ChallengeResponse[redacted]";
    }
}
