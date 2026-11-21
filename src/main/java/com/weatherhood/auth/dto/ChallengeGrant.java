package com.weatherhood.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 앱의 다음 Apple 로그인에 연결할 일회용 challenge.
 *
 * @param id        challenge 식별 UUID
 * @param nonce     Apple 인증 요청에 변형 없이 전달할 무작위 값
 * @param expiresAt UTC 기준 절대 만료 시각
 */
public record ChallengeGrant(UUID id, String nonce, Instant expiresAt) {
    @Override
    public String toString() {
        return "ChallengeGrant[redacted]";
    }
}
