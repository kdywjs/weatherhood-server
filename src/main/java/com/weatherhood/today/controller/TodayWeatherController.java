package com.weatherhood.today.controller;

import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import com.weatherhood.common.response.ApiResponse;
import com.weatherhood.today.dto.TodayWeatherResponse;
import com.weatherhood.today.service.TodayWeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오늘 날씨의 HTTP 요청 검증과 응답 DTO 변환을 담당하는 컨트롤러.
 *
 * <p>공급처 조회와 저장 판단은 서비스에 위임하고 영속성 엔티티를 직접 반환하지 않는다.
 */
@RestController
public class TodayWeatherController {
    private final TodayWeatherService todayWeatherService;

    /**
     * 의존 객체와 외부 설정을 받아 TodayWeatherController를 구성한다.
     *
     * @param todayWeatherService 오늘 날씨 수집·조회 서비스
     */
    public TodayWeatherController(TodayWeatherService todayWeatherService) {
        this.todayWeatherService = todayWeatherService;
    }

    /**
     * 지역 요청을 검증하고 부분 누락을 허용한 오늘 날씨를 반환한다.
     *
     * @param query 중복 없이 하나의 regionId만 포함한 쿼리
     * @return HTTP 상태와 공통 응답 객체
     */
    @GetMapping("/api/v1/weather/today")
    public ResponseEntity<ApiResponse<TodayWeatherResponse>> getTodayWeather(
            @RequestParam MultiValueMap<String, String> query) {
        var regionIds = query.get("regionId");
        if (regionIds == null || regionIds.size() != 1 || regionIds.getFirst().isBlank()
                || regionIds.getFirst().length() > 128
                || query.size() != 1) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        TodayWeatherResponse response = TodayWeatherResponse.from(
                todayWeatherService.getTodayWeather(regionIds.getFirst()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
