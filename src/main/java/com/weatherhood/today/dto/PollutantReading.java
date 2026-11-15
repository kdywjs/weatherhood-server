package com.weatherhood.today.dto;

import java.time.OffsetDateTime;

/**
 * 하나의 미세먼지 항목에 대한 시간 평균 측정값.
 *
 * @param concentration 농도(µg/m³), 미확보 시 null
 * @param grade         공급처의 1시간 등급, 미확보 시 null; 24시간 등급과 구분
 * @param periodStart   집계 시작 시각(포함)
 * @param periodEnd     집계 종료 시각(제외); 시간별 응답은 이 시각의 슬롯에 연결
 */
public record PollutantReading(Double concentration, String grade, OffsetDateTime periodStart,
                               OffsetDateTime periodEnd) {
}
