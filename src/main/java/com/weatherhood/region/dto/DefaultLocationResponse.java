package com.weatherhood.region.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * current_location이면 region은 null, saved_region이면 저장한 지역 객체다.
 *
 * @param type   current_location 또는 saved_region
 * @param region 행정동 식별 정보
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DefaultLocationResponse(
        
       
        @Schema(description = "current_location 또는 saved_region", example = "current_location")
        String type,
       
        @Schema(description = "기본 저장 지역 객체, 현재 위치이면 null") RegionResponse region)
        {
}
