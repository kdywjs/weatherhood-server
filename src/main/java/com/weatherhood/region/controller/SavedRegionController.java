package com.weatherhood.region.controller;

import com.weatherhood.auth.dto.SessionIdentity;
import com.weatherhood.common.response.ApiResponse;
import com.weatherhood.region.dto.DefaultLocationRequest;
import com.weatherhood.region.dto.RegionOrderRequest;
import com.weatherhood.region.dto.SavedRegionsResponse;
import com.weatherhood.region.service.SavedRegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검증된 사용자만 자신의 지역 목록과 기본값을 읽고 변경한다.
 */
@RestController
@Tag(name = "내 지역", description = "서버 세션으로 보호되는 사용자별 지역 설정")
@SecurityRequirement(name = "bearerAuth")
public class SavedRegionController {
    private final SavedRegionService regions;

    /**
     * 의존 객체와 외부 설정을 받아 SavedRegionController를 구성한다.
     *
     * @param regions 지역 조회 서비스 또는 저장소
     */
    public SavedRegionController(SavedRegionService regions) {
        this.regions = regions;
    }

    /**
     * 사용자별 저장 순서와 실행 기본 지역을 함께 조회한다.
     *
     * @param identity 인증 필터에서 검증한 현재 사용자와 세션
     * @return HTTP 상태와 공통 응답 객체
     */
    @GetMapping("/api/v1/me/regions")
    @Operation(summary = "저장 지역·기본 지역 조회",
            description = "저장 순서대로 반환합니다. 현재 위치는 저장 항목에 포함하지 않습니다.")
    public ResponseEntity<ApiResponse<SavedRegionsResponse>> getSavedLocations(
            @AuthenticationPrincipal SessionIdentity identity) {
        return ResponseEntity.ok(ApiResponse.success(SavedRegionsResponse.from(
                regions.getSavedLocations(identity.userId()))));
    }

    /**
     * 지역을 목록 끝에 추가하며 중복은 유지하고 최대 10개를 허용한다.
     *
     * @param identity 인증 필터에서 검증한 현재 사용자와 세션
     * @param regionId 내부 행정동 ID
     * @return HTTP 상태와 공통 응답 객체
     */
    @PutMapping("/api/v1/me/regions/{regionId}")
    @Operation(summary = "지역 저장",
            description = "새 지역은 끝에 추가하며 최대 10개입니다. 중복은 기존 순서를 유지하고 성공합니다.")
    public ResponseEntity<ApiResponse<SavedRegionsResponse>> addSavedRegion(
            @AuthenticationPrincipal SessionIdentity identity, @PathVariable String regionId) {
        return ResponseEntity.ok(ApiResponse.success(SavedRegionsResponse.from(
                regions.addSavedRegion(identity.userId(), regionId))));
    }

    /**
     * 저장 항목을 제거하고 삭제한 기본 지역은 현재 위치로 되돌린다.
     *
     * @param identity 인증 필터에서 검증한 현재 사용자와 세션
     * @param regionId 내부 행정동 ID
     * @return HTTP 상태와 공통 응답 객체
     */
    @DeleteMapping("/api/v1/me/regions/{regionId}")
    @Operation(summary = "저장 지역 삭제",
            description = "없는 저장 항목도 성공합니다. 기본 지역을 삭제하면 현재 위치로 복귀하며 앱의 현재 표시 날씨는 바꾸지 않습니다.")
    public ResponseEntity<ApiResponse<SavedRegionsResponse>> deleteSavedRegion(
            @AuthenticationPrincipal SessionIdentity identity, @PathVariable String regionId) {
        return ResponseEntity.ok(ApiResponse.success(SavedRegionsResponse.from(
                regions.deleteSavedRegion(identity.userId(), regionId))));
    }

    /**
     * 현재 저장 집합 전체가 일치할 때만 요청한 순서로 변경한다.
     *
     * @param identity 인증 필터에서 검증한 현재 사용자와 세션
     * @param request  검증할 HTTP 요청 본문
     * @return HTTP 상태와 공통 응답 객체
     */
    @PutMapping("/api/v1/me/regions/order")
    @Operation(summary = "저장 지역 순서 변경",
            description = "현재 저장 집합 전체를 중복 없이 보내야 합니다. 집합이 달라졌으면 409 REGION005 후 목록을 다시 조회합니다.")
    public ResponseEntity<ApiResponse<SavedRegionsResponse>> reorderSavedRegions(
            @AuthenticationPrincipal SessionIdentity identity, @Valid @RequestBody RegionOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SavedRegionsResponse.from(
                regions.reorderSavedRegions(identity.userId(), request.regionIds()))));
    }

    /**
     * 현재 위치 또는 이미 저장한 지역을 실행 기본값으로 지정한다.
     *
     * @param identity 인증 필터에서 검증한 현재 사용자와 세션
     * @param request  검증할 HTTP 요청 본문
     * @return HTTP 상태와 공통 응답 객체
     */
    @PutMapping("/api/v1/me/default-location")
    @Operation(summary = "실행 기본 지역 변경",
            description = "current_location 또는 saved_region을 사용합니다. 저장하지 않은 지역은 기본으로 지정할 수 없습니다.")
    public ResponseEntity<ApiResponse<SavedRegionsResponse>> updateDefaultLocation(
            @AuthenticationPrincipal SessionIdentity identity, @Valid @RequestBody DefaultLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SavedRegionsResponse.from(
                regions.updateDefaultLocation(identity.userId(), request.type(), request.regionId()))));
    }
}
