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
    /**
     * 성공 데이터와 오류 중 하나만 존재하는 응답을 생성한다.
     *
     * @param data  성공 응답 데이터, 해당 결과가 아니면 null
     * @param error 실패 오류 정보, 해당 결과가 아니면 null
     */
    public ApiResponse {
        if ((data == null) == (error == null)) {
            throw new IllegalArgumentException("Response must contain either data or error");
        }
    }

    /**
     * 성공 데이터를 공통 응답으로 감싼다.
     *
     * @param <T>  성공 데이터 타입
     * @param data null이 아닌 성공 응답 데이터
     * @return 성공 공통 응답
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Objects.requireNonNull(data), null);
    }

    /**
     * 공개 오류 코드·메시지와 재시도 정보를 공통 응답으로 감싼다.
     *
     * @param code              공개 오류 식별자
     * @param message           클라이언트에 공개할 오류 메시지
     * @param retryAfterSeconds 다음 재시도까지 기다릴 초, 알 수 없으면 null
     * @return 실패 공통 응답
     */
    public static ApiResponse<Void> failure(String code, String message, Long retryAfterSeconds) {
        return new ApiResponse<>(null, new ApiError(code, message, retryAfterSeconds));
    }

    /**
     * 공개 오류 코드·메시지와 선택적인 재시도 지연.
     *
     * @param code              공개 오류 식별자
     * @param message           클라이언트에 공개할 오류 메시지
     * @param retryAfterSeconds 다음 재시도까지 기다릴 초, 알 수 없으면 null
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ApiError(String code, String message, Long retryAfterSeconds) {
    }
}
