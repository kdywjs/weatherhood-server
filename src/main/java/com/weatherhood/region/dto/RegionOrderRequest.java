package com.weatherhood.region.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 저장 집합 전체를 원하는 순서대로 보내며 누락·중복은 허용하지 않는다.
 *
 * @param regionIds 원하는 순서의 전체 저장 지역 ID 목록
 */
public record RegionOrderRequest(
        
        @NotNull @Size(max = 10)
        @Schema(description = "원하는 순서의 전체 저장 지역 ID 목록, 중복·누락 불가") List<@NotBlank @Size(max = 128) String> regionIds)
        {
}
