package com.weatherhood.common.exception;


/**
 * 클라이언트와 공유하는 안정적인 오류 식별자와 공개 메시지.
 *
 * <p>응답 코드는 {@link #code()}를 사용하며 enum 이름이나 순서로 생성하지 않는다.
 * 발급한 코드의 의미를 변경하거나 다른 오류에 재사용하지 않는다.
 */
public enum ErrorCode {
    INVALID_REQUEST("INVALID_REQUEST", "요청 형식을 확인해 주세요."),
    REGION_NOT_FOUND("REGION_NOT_FOUND", "지역을 찾을 수 없습니다."),
    UNSUPPORTED_REGION("UNSUPPORTED_REGION", "지원하지 않는 지역입니다."),
    WEATHER_UNAVAILABLE("WEATHER_UNAVAILABLE", "오늘 날씨 정보를 불러올 수 없습니다."),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다."),
    NOT_ACCEPTABLE("NOT_ACCEPTABLE", "지원하지 않는 응답 형식입니다."),
    NOT_FOUND("NOT_FOUND", "요청한 경로를 찾을 수 없습니다."),
    INTERNAL_ERROR("INTERNAL_ERROR", "요청을 처리할 수 없습니다.");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
