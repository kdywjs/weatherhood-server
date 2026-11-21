package com.weatherhood.auth.dto;

import java.util.UUID;

/**
 * 검증된 세션의 내부 사용자 ID와 폐기용 해시. 원문 토큰을 보관하지 않는다.
 *
 * @param userId    인증된 사용자의 내부 UUID
 * @param tokenHash 원문 세션 토큰의 SHA-256 해시
 */
public record SessionIdentity(UUID userId, String tokenHash) {
}
