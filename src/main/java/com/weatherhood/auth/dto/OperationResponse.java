package com.weatherhood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 상태 변경이 완료됐음을 나타내는 응답.
 *
 * @param completed 작업이 완료됐으면 true
 */
public record OperationResponse(
       
        @Schema(description = "요청한 변경 완료 여부") boolean completed)
        {
}
