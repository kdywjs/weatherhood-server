package com.weatherhood.today.dto;


/**
 * PM10과 PM2.5를 구분하는 대기질 요약.
 *
 * @param pm10 미세먼지 측정값, 없으면 null
 * @param pm25 초미세먼지 측정값, 없으면 null
 */
public record AirQuality(PollutantReading pm10, PollutantReading pm25) {
    /**
     * 두 미세먼지 항목 중 하나라도 있으면 대기질 객체를 만든다.
     *
     * @param a PM10 측정값, 없으면 null
     * @param b PM2.5 측정값, 없으면 null
     * @return 두 항목이 모두 null이면 null, 아니면 대기질 요약
     */
    public static AirQuality of(PollutantReading a, PollutantReading b) {
        return a == null && b == null ? null : new AirQuality(a, b);
    }
}
