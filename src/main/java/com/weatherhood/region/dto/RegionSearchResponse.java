package com.weatherhood.region.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 페이지당 최대 20개의 공개 지역 검색 응답.
 *
 * @param items   결과 목록
 * @param page    0부터 시작하는 페이지 번호
 * @param hasNext 다음 페이지가 있으면 true
 */
public record RegionSearchResponse(
       
        @Schema(description = "최대 20개의 지역 검색 결과")
        List<RegionResponse> items,
        
        @Schema(description = "0부터 시작하는 페이지 번호", example = "0")
        int page,
        
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext)
        {
    /**
     * 서비스 또는 저장소 결과를 응답에 필요한 지역·날씨 정보로 변환한다.
     *
     * @param result 페이지 조회 결과
     * @return 변환한 RegionSearchResponse 객체
     */
    public static RegionSearchResponse from(RegionSearch result) {
        return new RegionSearchResponse(result.items().stream().map(RegionResponse::from).toList(),
                result.page(), result.hasNext());
    }
}
