package com.weatherhood.today.dto;


/**
 * 현재 확인 가능한 최신 관측 요약.
 *
 * @param temperatureC 최신 실황 기온(섭씨), 미확보 또는 허용 기한 초과 시 null
 * @param airQuality   최신 미세먼지 측정 요약, 모두 없으면 null; 하루 평균이나 예보가 아님
 */
public record CurrentWeather(Double temperatureC, AirQuality airQuality) {
}
