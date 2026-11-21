package com.weatherhood.today.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 한국 시각의 한 시간 슬롯에 제공하는 날씨 값.
 *
 * <p>온도는 섭씨, 풍속은 m/s, 습도·강수확률은 %다. 각 항목은 null을 허용한다.
 * 강수량·미세먼지는 별도의 집계 구간을 가지며 슬롯 시각과 측정 의미를 혼동하지 않는다.
 *
 * @param temperatureC                    해당 시각의 기온(섭씨), 없으면 null
 * @param sky                             하늘 상태 코드, 없으면 null
 * @param precipitationType               강수 형태 코드, 없으면 null
 * @param precipitationAmount             집계 구간을 포함한 강수량, 없으면 null
 * @param windSpeedMps                    풍속(m/s), 없으면 null
 * @param humidityPercent                 상대습도(%), 없으면 null
 * @param precipitationProbabilityPercent 예보 강수확률(%), 없으면 null
 * @param airQuality                      슬롯 종료 시각에 대응하는 대기질, 없으면 null
 */
public record HourlyWeather(Double temperatureC, String sky, String precipitationType,
                            Precipitation precipitationAmount, Double windSpeedMps, Double humidityPercent,
                            Double precipitationProbabilityPercent, AirQuality airQuality) {
    /**
     * 이 시간 슬롯의 모든 날씨 항목이 누락됐는지 확인한다.
     *
     * @return 모든 항목이 null이면 true
     */
    @JsonIgnore
    public boolean isEmpty() {
        return temperatureC == null && sky == null && precipitationType == null && precipitationAmount == null
                && windSpeedMps == null && humidityPercent == null && precipitationProbabilityPercent == null
                && airQuality == null;
    }
}
