package com.weatherhood.today.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * 오늘 날씨 API의 성공 payload. 공통 응답의 data 안에 배치한다.
 *
 * <p>hourly는 00:00~23:00의 24개 키를 가지며 자료가 없는 시간은 null이다.
 * JSON 객체의 순서는 계약으로 보장하지 않는다.
 *
 * @param region   선택한 내부 지역 ID와 표시명
 * @param date     요청 진입 시 고정한 한국 날짜
 * @param timezone Asia/Seoul
 * @param current  최신 관측 요약
 * @param daily    하루 예보 최저·최고 기온과 일출·일몰 시각
 * @param hourly   00:00~23:00 시간 키와 날씨 값, 누락 슬롯은 null
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TodayWeatherResponse(RegionResponse region, LocalDate date, String timezone,
                                   CurrentWeather current, DailyWeather daily, Map<String, HourlyWeather> hourly) {
    public static TodayWeatherResponse from(TodayWeatherData today) {
        return new TodayWeatherResponse(today.region(), today.date(), today.timezone(),
                today.current(), today.daily(), Collections.unmodifiableMap(new LinkedHashMap<>(today.hourly())));
    }
}
