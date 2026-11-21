package com.weatherhood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 내부 사용자 ID만 노출하는 사용자 응답.
 *
 * @param id 내부 사용자 UUID
 */
public record UserResponse(
        @Schema(description = "내부 사용자 UUID") UUID id)
        {
}
