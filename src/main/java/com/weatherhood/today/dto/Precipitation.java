package com.weatherhood.today.dto;


/**
 * 공급 집계 구간을 보존하는 강수량 값. 정확한 수치와 범위 두 형식만 허용한다.
 */
public sealed interface Precipitation permits ExactPrecipitation, PrecipitationRange {
}
