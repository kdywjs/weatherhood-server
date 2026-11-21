package com.weatherhood.region.dto;

import java.util.List;

/**
 * 전체 개수 계산 없이 다음 페이지 존재 여부를 제공하는 검색 결과.
 *
 * @param items   결과 목록
 * @param page    0부터 시작하는 페이지 번호
 * @param hasNext 다음 페이지가 있으면 true
 */
public record RegionSearch(List<RegionInfo> items, int page, boolean hasNext) {
}
