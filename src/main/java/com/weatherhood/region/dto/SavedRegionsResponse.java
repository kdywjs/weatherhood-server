package com.weatherhood.region.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 저장된 순서의 지역과 현재 화면 선택과 독립적인 실행 기본값.
 *
 * @param items           결과 목록
 * @param defaultLocation 현재 위치 또는 저장 지역으로 설정한 실행 기본값
 */
public record SavedRegionsResponse(
        @Schema(description = "사용자 지정 순서의 저장 지역")
        List<RegionResponse> items,
        @Schema(description = "다음 실행의 기본 지역") DefaultLocationResponse defaultLocation)
        {
    /**
     * 서비스 또는 저장소 결과를 응답에 필요한 지역·날씨 정보로 변환한다.
     *
     * @param locations 사용자별 지역 목록과 기본 지역
     * @return 변환한 SavedRegionsResponse 객체
     */
    public static SavedRegionsResponse from(SavedLocations locations) {
        RegionResponse region =
                locations.defaultRegion() == null ? null : RegionResponse.from(locations.defaultRegion());
        return new SavedRegionsResponse(locations.items().stream().map(RegionResponse::from).toList(),
                new DefaultLocationResponse(region == null ? "current_location" : "saved_region", region));
    }
}
