package com.weatherhood.today.client;

import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.exception.WeatherProviderException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 정규화한 날씨 자료를 반환하는 외부 공급처 호출 계약.
 *
 * <p>인증키와 원본 응답을 서비스로 전달하지 않는다. 호출 실패는 공급처 전용 예외로 구분한다.
 */
public interface WeatherProvider {
    record Request(WeatherSource source, String location, OffsetDateTime release) {
        public String key() {
            return source + "|" + location + "|" + release;
        }

        public String provider() {
            return switch (source) {
                case AIR -> "air";
                case SUN -> "kasi";
                default -> "kma";
            };
        }
    }

    record Result(List<WeatherDatum> values, boolean complete) {
    }

    /**
     * 공급 자료를 한 번 조회한다.
     *
     * @param request 공급 종류·위치·발표 기준 시각
     * @return 정규화 자료와 응답 완전성 판단값
     * @throws WeatherProviderException 인증·통신·응답 해석·중단 등 공급처 호출 실패
     */
    Result fetchWeatherData(Request request) throws WeatherProviderException;

}
