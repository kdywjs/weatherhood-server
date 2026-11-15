package com.weatherhood.today.dto;

import java.time.OffsetDateTime;

/**
 * 한 출처에서 얻은 단일 항목의 정규화 자료.
 *
 * @param source   관측·예보·대기질 출처
 * @param location 기상 격자 식별 문자열 또는 측정소명
 * @param field    항목 식별자
 * @param target   관측 또는 예보 대상 시각
 * @param issued   공급 자료의 발표 시각 또는 관측 시각
 * @param value    항목에 대응하는 숫자·상태 문자열·강수량·미세먼지 값; null은 저장하지 않음
 */
public record WeatherDatum(WeatherSource source, String location, WeatherField field, OffsetDateTime target,
                           OffsetDateTime issued, Object value) {
}
