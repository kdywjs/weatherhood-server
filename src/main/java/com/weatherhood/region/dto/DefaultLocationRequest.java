package com.weatherhood.region.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 기본 위치 모드와 저장 지역 ID. 모드별 null 규칙은 서비스에서 검증한다.
 *
 * @param type     current_location 또는 saved_region
 * @param regionId 내부 행정동 ID
 */
public record DefaultLocationRequest(
        
        @NotBlank @Pattern(regexp = "current_location|saved_region")
        @Schema(description = "current_location 또는 saved_region", example = "current_location")
        String type,
        @Size(max = 128)
        @Schema(description = "saved_region이면 저장 지역 ID, current_location이면 null") String regionId)
        {
}
