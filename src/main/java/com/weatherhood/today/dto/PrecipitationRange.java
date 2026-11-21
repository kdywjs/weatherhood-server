package com.weatherhood.today.dto;

import java.time.OffsetDateTime;

/**
 * 상·하한과 경계 포함 여부를 보존하는 강수량 범위.
 *
 * @param kind           형식 식별자 range
 * @param lowerBound     하한(mm)
 * @param upperBound     상한(mm), null이면 상한 없음
 * @param lowerInclusive 하한 포함 여부
 * @param upperInclusive 상한 포함 여부; 상한이 없으면 false
 * @param unit           단위 mm
 * @param periodStart    집계 시작 시각(포함)
 * @param periodEnd      집계 종료 시각(제외)
 */
public record PrecipitationRange(String kind, double lowerBound, Double upperBound, boolean lowerInclusive,
                                 boolean upperInclusive, String unit, OffsetDateTime periodStart,
                                 OffsetDateTime periodEnd) implements Precipitation {
    /**
     * 단위와 경계 포함 여부를 보존한 강수량 범위를 생성한다.
     *
     * @param lower 강수량 하한(mm)
     * @param upper 강수량 상한(mm), null이면 상한 없음
     * @param li    하한 포함 여부
     * @param ui    상한 포함 여부
     * @param start 강수량 집계 시작 시각(포함)
     * @param end   강수량 집계 종료 시각(제외)
     */
    public PrecipitationRange(double lower, Double upper, boolean li, boolean ui, OffsetDateTime start,
                              OffsetDateTime end) {
        this("range", lower, upper, li, ui, "mm", start, end);
    }
}
