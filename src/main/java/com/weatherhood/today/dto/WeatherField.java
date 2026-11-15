package com.weatherhood.today.dto;


/**
 * 정규화 자료의 항목 식별자. 단위와 공개 필드 형식은 해당 응답 DTO를 따른다.
 */
public enum WeatherField {
    TEMPERATURE,
    SKY,
    PRECIPITATION_TYPE,
    PRECIPITATION_AMOUNT,
    WIND,
    HUMIDITY,
    PROBABILITY,
    MIN,
    MAX,
    PM10,
    PM25,
    SUNRISE,
    SUNSET
}
