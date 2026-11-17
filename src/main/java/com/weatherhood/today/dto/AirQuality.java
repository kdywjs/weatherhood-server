package com.weatherhood.today.dto;


/**
 * PM10과 PM2.5를 구분하는 대기질 요약.
 *
 * @param pm10 미세먼지 측정값, 없으면 null
 * @param pm25 초미세먼지 측정값, 없으면 null
 */
public record AirQuality(PollutantReading pm10, PollutantReading pm25) {
    public static AirQuality of(PollutantReading a, PollutantReading b) {
        return a == null && b == null ? null : new AirQuality(a, b);
    }
}
