package com.weatherhood.region.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * WGS84 좌표 입력. 사용자의 정확한 위치를 로그에 출력하지 않는다.
 *
 * @param latitude  WGS84 위도(도)
 * @param longitude WGS84 경도(도)
 */
public record ResolveRegionRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90")
        @Schema(description = "WGS84 위도, -90~90도", example = "37.4012191")
        Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180")
        @Schema(description = "WGS84 경도, -180~180도", example = "127.1086228") Double longitude)
        {
    @Override
    public String toString() {
        return "ResolveRegionRequest[redacted]";
    }
}
