package com.weatherhood.region.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 오늘 날씨와 동일한 내부 지역 ID·전체 이름을 제공한다.
 *
 * @param id   내부 행정동 ID
 * @param name 상위 행정구역을 포함한 전체 지역명
 */
public record RegionResponse(
        @Schema(description = "오늘 날씨 요청과 공유하는 내부 지역 ID", example = "51bf94b3-e51d-5ef7-b00f-45769ea0e8cb")
        String id,
        @Schema(description = "상위 행정구역을 포함한 전체 지역명", example = "경기도 성남시분당구 삼평동") String name)
        {
    /**
     * 서비스 또는 저장소 결과를 응답에 필요한 지역·날씨 정보로 변환한다.
     *
     * @param info 서비스의 지역 식별 결과
     * @return 변환한 RegionResponse 객체
     */
    public static RegionResponse from(RegionInfo info) {
        return new RegionResponse(info.id(), info.name());
    }
}
