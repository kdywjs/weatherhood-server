package com.weatherhood.region.dto;

import java.util.List;

/**
 * 사용자별 저장 순서와 다음 실행 기본 지역. defaultRegion null은 현재 위치다.
 *
 * @param items         결과 목록
 * @param defaultRegion 실행 기본 지역, null이면 현재 위치
 */
public record SavedLocations(List<RegionInfo> items, RegionInfo defaultRegion) {
}
