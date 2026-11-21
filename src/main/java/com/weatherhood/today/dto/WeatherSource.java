package com.weatherhood.today.dto;


/**
 * 자료의 출처 유형. 관측·초단기예보·단기예보·대기질·출몰시각을 서로 구분한다.
 */
public enum WeatherSource {
    /**
     * 기상청 초단기실황의 관측값.
     */
    OBSERVATION,
    /**
     * 기상청 초단기예보.
     */
    ULTRA,
    /**
     * 기상청 단기예보.
     */
    SHORT,
    /**
     * 에어코리아 대기질 관측 자료.
     */
    AIR,
    /**
     * 한국천문연구원 일출·일몰 자료.
     */
    SUN
}
