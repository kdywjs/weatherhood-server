package com.weatherhood.today.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * 서비스에서 조합한 오늘 날씨 결과.
 *
 * <p>date와 timezone으로 날짜 범위를 식별하고 hourly에는 00:00~23:00 키를 유지한다.
 * 공개 HTTP 응답으로 변환할 때는 {@link TodayWeatherResponse}를 사용한다.
 *
 * @param region   선택한 지역
 * @param date     한국 날짜
 * @param timezone 시간대 식별자
 * @param current  현재 관측 요약
 * @param daily    하루 예보값
 * @param hourly   시간 키별 조합 결과
 */
public record TodayWeatherData(RegionResponse region, LocalDate date, String timezone, CurrentWeather current,
                               DailyWeather daily, Map<String, HourlyWeather> hourly) {
}
