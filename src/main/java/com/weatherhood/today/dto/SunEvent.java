package com.weatherhood.today.dto;

import java.time.OffsetDateTime;

/**
 * 공급 기준 지역의 일출 또는 일몰 시각과 내부 출처 정보.
 *
 * @param time               한국 시각의 출몰 시각
 * @param referenceLocation  공급처가 선택한 기준 지역명
 * @param referenceLatitude  기준 지역 위도
 * @param referenceLongitude 기준 지역 경도
 */
public record SunEvent(OffsetDateTime time, String referenceLocation, Double referenceLatitude,
                       Double referenceLongitude) {
}
