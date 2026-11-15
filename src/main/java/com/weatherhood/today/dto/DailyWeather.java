package com.weatherhood.today.dto;


import java.time.OffsetDateTime;

/**
 * 오늘 하루의 최저·최고 예보와 일출·일몰 시각.
 *
 * @param temperatureMinC 예보 최저 기온(섭씨), 미확보 시 null
 * @param temperatureMaxC 예보 최고 기온(섭씨), 미확보 시 null
 * @param sunriseAt       기준 지역의 일출 시각, 미확보 시 null
 * @param sunsetAt        기준 지역의 일몰 시각, 미확보 시 null
 */
public record DailyWeather(Double temperatureMinC, Double temperatureMaxC, OffsetDateTime sunriseAt,
                           OffsetDateTime sunsetAt) {
}
