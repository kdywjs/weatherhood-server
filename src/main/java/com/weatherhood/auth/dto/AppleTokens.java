package com.weatherhood.auth.dto;

/**
 * Apple 코드 교환 결과. 비밀 토큰을 로그에 출력하지 않는다.
 *
 * @param identityToken Apple이 서명한 사용자 식별 JWT
 * @param refreshToken  Apple 세션 검증·폐기에 사용할 원문 refresh token
 */
public record AppleTokens(String identityToken, String refreshToken) {
    @Override
    public String toString() {
        return "AppleTokens[redacted]";
    }
}
