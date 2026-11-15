package com.weatherhood.today.dto;


/**
 * 조회 대상 지역의 식별자와 표시명.
 *
 * @param id   내부 지역 ID
 * @param name 상위 행정구역을 포함한 표시명
 */
public record RegionResponse(String id, String name) {
}
