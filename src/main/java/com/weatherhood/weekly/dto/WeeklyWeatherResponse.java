package com.weatherhood.weekly.dto;

import com.weatherhood.today.dto.RegionResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 주간 예보 목록 응답.
 *
 * @param region 행정동
 * @param timezone 날짜와 오전·오후를 구분하는 시간대
 * @param daily 예보가 있는 날짜의 오름차순 목록
 */
public record WeeklyWeatherResponse(RegionResponse region, String timezone, List<Day> daily) {
    /**
     * 한 날짜의 기온 범위와 오전·오후 예보.
     *
     * @param date 예보 대상 날짜
     * @param temperatureMinC 기상청 일 최저 예보
     * @param temperatureMaxC 기상청 일 최고 예보
     * @param morning 오전 대표 예보
     * @param afternoon 오후 대표 예보
     */
    public record Day(LocalDate date, Double temperatureMinC, Double temperatureMaxC, Period morning,
                      Period afternoon) {
    }

    /**
     * 실제 예보 시각의 하늘과 강수 형태.
     *
     * @param forecastAt 선택한 실제 예보 시각
     * @param sky 해당 시각의 하늘 상태
     * @param precipitationType 해당 시각의 강수 형태
     */
    public record Period(OffsetDateTime forecastAt, String sky, String precipitationType) {
    }
}
