package com.weatherhood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 서버 challenge와 native Apple 로그인에서 수신한 자격 증명.
 *
 * @param challengeId       서버가 발급한 일회용 challenge ID
 * @param identityToken     Apple이 서명한 사용자 식별 JWT
 * @param authorizationCode Apple에서 받은 일회용 인증 코드
 */
public record AppleLoginRequest(
        @NotNull
        @Schema(description = "로그인 준비 API에서 받은 challenge ID")
        UUID challengeId,
        @NotBlank @Size(max = 16384)
        @Schema(description = "Apple이 반환한 identity token JWT")
        String identityToken,
        @NotBlank @Size(max = 4096)
        @Schema(description = "Apple이 반환한 일회용 인증 코드") String authorizationCode)
        {
    @Override
    public String toString() {
        return "AppleLoginRequest[redacted]";
    }
}
