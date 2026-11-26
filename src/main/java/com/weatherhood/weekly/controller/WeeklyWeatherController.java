package com.weatherhood.weekly.controller;

import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import com.weatherhood.common.response.ApiResponse;
import com.weatherhood.weekly.dto.WeeklyWeatherResponse;
import com.weatherhood.weekly.service.WeeklyWeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 비로그인 행정동 주간 날씨 조회. */
@RestController
public class WeeklyWeatherController {
    private final WeeklyWeatherService service;

    /**
     * 주간 조회 컨트롤러를 구성한다.
     *
     * @param service 주간 예보 조회 서비스
     */
    public WeeklyWeatherController(WeeklyWeatherService service) {
        this.service = service;
    }

    /**
     * 요청을 검증하고 날짜별 목록을 반환한다.
     *
     * @param query 단일 regionId 쿼리
     * @return 자료가 확보된 날짜별 날씨
     */
    @Operation(summary = "주간 날씨 조회", description = "한국 기준 오늘부터 7일 안에 예보가 있는 날짜만 반환합니다. "
                    + "각 날짜에 09시·15시에 각각 가장 가까운 오전·오후 예보를 제공하며, 자료가 없는 시간대는 null입니다.",
            parameters = @Parameter(name = "regionId", in = ParameterIn.QUERY,
                    required = true, description = "행정동 ID"))
    @GetMapping("/api/v1/weather/weekly")
    public ResponseEntity<ApiResponse<WeeklyWeatherResponse>> findWeeklyWeather(
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> query) {
        var ids = query.get("regionId");
        if (query.size() != 1 || ids == null || ids.size() != 1 || ids.getFirst().isBlank()
                || ids.getFirst().length() > 128) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        return ResponseEntity.ok(ApiResponse.success(service.findWeeklyWeather(ids.getFirst())));
    }
}
