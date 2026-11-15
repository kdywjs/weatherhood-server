package com.weatherhood.today.dto;

import java.time.OffsetDateTime;

/**
 * 정확한 수치로 제공하는 강수량.
 *
 * @param kind        형식 식별자 exact
 * @param value       강수량(mm), 0은 강수 없음
 * @param unit        단위 mm
 * @param periodStart 집계 시작 시각(포함)
 * @param periodEnd   집계 종료 시각(제외)
 */
public record ExactPrecipitation(String kind, double value, String unit, OffsetDateTime periodStart,
                                 OffsetDateTime periodEnd) implements Precipitation {
    public ExactPrecipitation(double value, OffsetDateTime start, OffsetDateTime end) {
        this("exact", value, "mm", start, end);
    }
}
