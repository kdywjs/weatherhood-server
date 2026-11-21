package com.weatherhood.region.dto;

import com.weatherhood.today.entity.WeatherRegion;

/**
 * 검색·좌표 매핑·개인 목록에서 공유하는 내부 지역 식별 정보.
 *
 * @param id   내부 행정동 ID
 * @param name 상위 행정구역을 포함한 전체 지역명
 */
public record RegionInfo(String id, String name) {
    /**
     * 서비스 또는 저장소 결과를 응답에 필요한 지역·날씨 정보로 변환한다.
     *
     * @param region 행정동 식별 정보
     * @return 행정동 내부 ID와 전체 이름
     */
    public static RegionInfo from(WeatherRegion region) {
        return new RegionInfo(region.id(), region.name());
    }
}
