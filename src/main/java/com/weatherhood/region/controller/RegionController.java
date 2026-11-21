package com.weatherhood.region.controller;

import com.weatherhood.common.response.ApiResponse;
import com.weatherhood.region.dto.RegionResponse;
import com.weatherhood.region.dto.RegionSearchResponse;
import com.weatherhood.region.dto.ResolveRegionRequest;
import com.weatherhood.region.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비로그인 지역 검색·ID 조회·현재 위치 매핑의 HTTP 경계.
 */
@RestController
@Tag(name = "공개 지역", description = "비로그인 행정동 검색과 현재 위치 확인")
public class RegionController {
    private final RegionService regions;

    /**
     * 의존 객체와 외부 설정을 받아 RegionController를 구성한다.
     *
     * @param regions 지역 조회 서비스 또는 저장소
     */
    public RegionController(RegionService regions) {
        this.regions = regions;
    }

    /**
     * 내부 공백을 보존해 전체 지역명을 검색하고 20개 단위로 반환한다.
     *
     * @param query 앞뒤 공백만 제거할 지역 검색어
     * @param page  0부터 시작하는 페이지 번호
     * @return HTTP 상태와 공통 응답 객체
     */
    @GetMapping("/api/v1/regions")
    @Operation(summary = "행정동 검색",
            description = "query는 앞뒤 공백만 제거한 2~100자입니다. 중간 공백·문자 순서를 유지해 검색합니다. page는 0부터 시작하며 페이지당 20개입니다.")
    public ResponseEntity<ApiResponse<RegionSearchResponse>> searchRegions(@RequestParam String query,
                                                                           @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(ApiResponse.success(RegionSearchResponse.from(regions.searchRegions(query, page))));
    }

    /**
     * 지원하는 행정동의 내부 ID와 전체 이름을 조회한다.
     *
     * @param regionId 내부 행정동 ID
     * @return HTTP 상태와 공통 응답 객체
     */
    @GetMapping("/api/v1/regions/{regionId}")
    @Operation(summary = "지역 ID 조회",
            description = "검색·GPS 변환·오늘 날씨에서 동일한 내부 지역 ID를 사용합니다.")
    public ResponseEntity<ApiResponse<RegionResponse>> getRegion(@PathVariable String regionId) {
        return ResponseEntity.ok(ApiResponse.success(RegionResponse.from(regions.getRegion(regionId))));
    }

    /**
     * GPS가 속한 행정동을 기존 지역 ID로 변환하며 좌표는 저장하지 않는다.
     *
     * @param request 검증할 HTTP 요청 본문
     * @return HTTP 상태와 공통 응답 객체
     */
    @PostMapping("/api/v1/regions/resolve")
    @Operation(summary = "현재 좌표의 행정동 확인",
            description = "WGS84 좌표를 카카오 H 코드와 내부 ID로 연결합니다. 좌표는 저장하지 않으며 지역 목록이나 기본값도 변경하지 않습니다.")
    public ResponseEntity<ApiResponse<RegionResponse>> resolveCoordinates(
            @Valid @RequestBody ResolveRegionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                RegionResponse.from(regions.resolveCoordinates(request.latitude(), request.longitude()))));
    }
}
