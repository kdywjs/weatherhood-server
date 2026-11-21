package com.weatherhood.today.dto;


/**
 * 정규화 자료의 항목 식별자. 단위와 공개 필드 형식은 해당 응답 DTO를 따른다.
 */
public enum WeatherField {
    /**
     * 기온(섭씨).
     */
    TEMPERATURE,
    /**
     * 정규화한 하늘 상태.
     */
    SKY,
    /**
     * 정규화한 강수 형태.
     */
    PRECIPITATION_TYPE,
    /**
     * 집계 구간과 함께 제공하는 강수량(mm).
     */
    PRECIPITATION_AMOUNT,
    /**
     * 풍속(m/s).
     */
    WIND,
    /**
     * 상대 습도(%).
     */
    HUMIDITY,
    /**
     * 강수 확률(%).
     */
    PROBABILITY,
    /**
     * 하루 최저 기온(섭씨).
     */
    MIN,
    /**
     * 하루 최고 기온(섭씨).
     */
    MAX,
    /**
     * 미세먼지 농도와 등급.
     */
    PM10,
    /**
     * 초미세먼지 농도와 등급.
     */
    PM25,
    /**
     * 일출 시각.
     */
    SUNRISE,
    /**
     * 일몰 시각.
     */
    SUNSET
}
