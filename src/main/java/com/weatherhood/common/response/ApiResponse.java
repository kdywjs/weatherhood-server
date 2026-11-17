package com.weatherhood.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;


/**
 * API 성공 데이터 또는 오류 중 하나를 담는 공통 응답.
 *
 * <p>성공은 error가 null이고 실패는 data가 null이다. HTTP 상태는 호출한 컨트롤러나 예외 처리기가 결정한다.
 *
 * @param <T>   성공 payload 타입
 * @param data  성공 데이터, 실패 시 null
 * @param error 오류 정보, 성공 시 null
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(T data, ApiError error) {
    public ApiResponse {
        if ((data == null) == (error == null)) {
            throw new IllegalArgumentException("Response must contain either data or error");
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Objects.requireNonNull(data), null);
    }

    public static ApiResponse<Void> failure(String code, String message, Long retryAfterSeconds) {
        return new ApiResponse<>(null, new ApiError(code, message, retryAfterSeconds));
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ApiError(String code, String message, Long retryAfterSeconds) {
    }
}
