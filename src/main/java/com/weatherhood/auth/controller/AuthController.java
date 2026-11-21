package com.weatherhood.auth.controller;

import com.weatherhood.auth.dto.AppleLoginRequest;
import com.weatherhood.auth.dto.ChallengeResponse;
import com.weatherhood.auth.dto.LoginResponse;
import com.weatherhood.auth.dto.OperationResponse;
import com.weatherhood.auth.dto.SessionIdentity;
import com.weatherhood.auth.dto.UserResponse;
import com.weatherhood.auth.service.AppleLoginService;
import com.weatherhood.auth.service.SessionService;
import com.weatherhood.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Apple 로그인·현재 기기 로그아웃·사용자 조회·탈퇴의 HTTP 경계.
 */
@RestController
@Tag(name = "인증", description = "Apple 로그인과 서버 세션·회원 탈퇴")
public class AuthController {
    private final AppleLoginService login;
    private final SessionService sessions;

    /**
     * 의존 객체와 외부 설정을 받아 AuthController를 구성한다.
     *
     * @param login    Apple 로그인 서비스
     * @param sessions 세션 저장 또는 인증 서비스
     */
    public AuthController(AppleLoginService login, SessionService sessions) {
        this.login = login;
        this.sessions = sessions;
    }

    /**
     * 앱의 Apple 인증 요청에 사용할 일회용 nonce를 발급한다.
     *
     * @return HTTP 상태와 공통 응답 객체
     */
    @PostMapping("/api/v1/auth/apple/challenges")
    @Operation(summary = "Apple 로그인 준비",
            description = "5분간 유효합니다. nonce를 변형 없이 Apple 로그인 요청에 전달합니다.")
    public ResponseEntity<ApiResponse<ChallengeResponse>> createAppleChallenge() {
        var challenge = login.createChallenge();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                new ChallengeResponse(challenge.id(), challenge.nonce(), challenge.expiresAt())));
    }

    /**
     * Apple 자격을 검증하고 30일 유효한 서버 세션을 발급한다.
     *
     * @param request 검증할 HTTP 요청 본문
     * @return HTTP 상태와 공통 응답 객체
     */
    @PostMapping("/api/v1/auth/apple")
    @Operation(summary = "Apple 로그인",
            description = "identityToken과 authorizationCode를 검증한 뒤 30일 유효한 서버 토큰을 반환합니다. "
                    + "검증 실패는 AUTH002, 공급처 장애는 AUTH003입니다.")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithApple(@Valid @RequestBody AppleLoginRequest request) {
        var grant = login.loginWithApple(request.challengeId(), request.identityToken(), request.authorizationCode());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                new LoginResponse(grant.token(), "Bearer", grant.expiresAt(), new UserResponse(grant.userId()))));
    }

    /**
     * 요청한 세션만 종료하고 다른 기기의 세션은 유지한다.
     *
     * @param identity 인증 필터에서 검증한 현재 사용자와 세션
     * @return HTTP 상태와 공통 응답 객체
     */
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/v1/auth/logout")
    @Operation(summary = "현재 세션 로그아웃",
            description = "요청에 사용한 서버 토큰만 종료합니다. 다른 세션은 유지됩니다.")
    public ResponseEntity<ApiResponse<OperationResponse>> logoutSession(
            @AuthenticationPrincipal SessionIdentity identity) {
        sessions.logoutSession(identity);
        return ResponseEntity.ok(ApiResponse.success(new OperationResponse(true)));
    }

    /**
     * 현재 세션에서 인증된 사용자의 내부 ID를 조회한다.
     *
     * @param identity 인증 필터에서 검증한 현재 사용자와 세션
     * @return HTTP 상태와 공통 응답 객체
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/v1/me")
    @Operation(summary = "내 사용자 정보 조회",
            description = "Apple 식별자나 이메일을 공개하지 않고 내부 사용자 ID만 반환합니다.")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal SessionIdentity identity) {
        return ResponseEntity.ok(ApiResponse.success(new UserResponse(identity.userId())));
    }

    /**
     * Apple 토큰을 폐기하고 계정과 모든 개인 저장 데이터를 삭제한다.
     *
     * @param identity 인증 필터에서 검증한 현재 사용자와 세션
     * @return HTTP 상태와 공통 응답 객체
     */
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/v1/me")
    @Operation(summary = "회원 탈퇴",
            description = "Apple 토큰 폐기에 성공하면 계정·모든 세션·저장 지역을 삭제합니다. 폐기 실패 시 503이며 재시도할 수 있습니다.")
    public ResponseEntity<ApiResponse<OperationResponse>> deleteAccount(
            @AuthenticationPrincipal SessionIdentity identity) {
        sessions.deleteAccount(identity);
        return ResponseEntity.ok(ApiResponse.success(new OperationResponse(true)));
    }
}
