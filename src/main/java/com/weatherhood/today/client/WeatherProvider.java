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
    /**
     * 공급 유형·공유 위치·발표 시각으로 식별하는 수집 요청.
     *
     * @param source   원본 공급 자료 유형
     * @param location 공급 자료를 공유하는 위치 키
     * @param release  공급 자료의 발표 시각
     */
    record Request(WeatherSource source, String location, OffsetDateTime release) {
        /**
         * 수집 요청을 식별하는 공급 유형·위치·발표 시각 키를 만든다.
         *
         * @return 요청을 공유·중복 제거할 수집 키
         */
        public String key() {
            return source + "|" + location + "|" + release;
        }

        /**
         * 일일 호출량과 공급처 전체 실패 제한에 사용할 공급처 이름을 반환한다.
         *
         * @return 기상청·에어코리아·한국천문연구원 중 공급처 식별자
         */
        public String provider() {
            return switch (source) {
                case AIR -> "air";
                case SUN -> "kasi";
                default -> "kma";
            };
        }
    }

    /**
     * 정규화한 수집 자료와 요청 범위의 확보 여부.
     *
     * @param values   정규화한 날씨 자료
     * @param complete 요청한 범위를 완전히 확보했으면 true
     */
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
