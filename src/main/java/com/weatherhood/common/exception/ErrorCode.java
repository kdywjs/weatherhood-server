package com.weatherhood.common.exception;


/**
 * 클라이언트와 공유하는 안정적인 오류 식별자와 공개 메시지.
 *
 * <p>응답 코드는 {@link #code()}를 사용하며 enum 이름이나 순서로 생성하지 않는다.
 * 발급한 코드의 의미를 변경하거나 다른 오류에 재사용하지 않는다.
 */
public enum ErrorCode {
    /**
     * 요청 형식을 확인해 주세요.
     */
    INVALID_REQUEST("INVALID_REQUEST", "요청 형식을 확인해 주세요."),
    /**
     * 지역을 찾을 수 없습니다.
     */
    REGION_NOT_FOUND("REGION_NOT_FOUND", "지역을 찾을 수 없습니다."),
    /**
     * 지원하지 않는 지역입니다.
     */
    UNSUPPORTED_REGION("UNSUPPORTED_REGION", "지원하지 않는 지역입니다."),
    /**
     * 오늘 날씨 정보를 불러올 수 없습니다.
     */
    WEATHER_UNAVAILABLE("WEATHER_UNAVAILABLE", "오늘 날씨 정보를 불러올 수 없습니다."),
    /** 주간 목록으로 제공할 유효한 예보가 없는 경우. */
    WEEKLY_WEATHER_UNAVAILABLE("WEEKLY_WEATHER_UNAVAILABLE", "주간 예보를 불러올 수 없습니다."),
    /**
     * 지원하지 않는 요청 방식입니다.
     */
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다."),
    /**
     * 지원하지 않는 요청 형식입니다.
     */
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다."),
    /**
     * 지원하지 않는 응답 형식입니다.
     */
    NOT_ACCEPTABLE("NOT_ACCEPTABLE", "지원하지 않는 응답 형식입니다."),
    /**
     * 요청한 경로를 찾을 수 없습니다.
     */
    NOT_FOUND("NOT_FOUND", "요청한 경로를 찾을 수 없습니다."),
    /**
     * 로그인이 필요합니다.
     */
    UNAUTHENTICATED("AUTH001", "로그인이 필요합니다."),
    /**
     * Apple 로그인을 다시 진행해 주세요.
     */
    INVALID_APPLE_CREDENTIAL("AUTH002", "Apple 로그인을 다시 진행해 주세요."),
    /**
     * 인증 서비스를 일시적으로 사용할 수 없습니다.
     */
    AUTH_UNAVAILABLE("AUTH003", "인증 서비스를 일시적으로 사용할 수 없습니다."),
    /**
     * 해당 위치의 국내 행정동을 찾을 수 없습니다.
     */
    LOCATION_NOT_FOUND("REGION001", "해당 위치의 국내 행정동을 찾을 수 없습니다."),
    /**
     * 지역 정보를 연결할 수 없습니다.
     */
    REGION_MAPPING_UNAVAILABLE("REGION002", "지역 정보를 연결할 수 없습니다."),
    /**
     * 현재 위치의 지역 정보를 불러올 수 없습니다.
     */
    LOCATION_UNAVAILABLE("REGION003", "현재 위치의 지역 정보를 불러올 수 없습니다."),
    /**
     * 지역은 최대 10개까지 저장할 수 있습니다.
     */
    SAVED_REGION_LIMIT("REGION004", "지역은 최대 10개까지 저장할 수 있습니다."),
    /**
     * 지역 목록을 다시 조회한 뒤 순서를 변경해 주세요.
     */
    REGION_ORDER_CONFLICT("REGION005", "지역 목록을 다시 조회한 뒤 순서를 변경해 주세요."),
    /**
     * 저장한 지역만 기본 지역으로 설정할 수 있습니다.
     */
    DEFAULT_REGION_NOT_SAVED("REGION006", "저장한 지역만 기본 지역으로 설정할 수 있습니다."),
    /**
     * 요청을 처리할 수 없습니다.
     */
    INTERNAL_ERROR("INTERNAL_ERROR", "요청을 처리할 수 없습니다.");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 클라이언트에 제공할 안정적인 오류 식별자를 반환한다.
     *
     * @return 클라이언트에 제공할 안정적인 오류 식별자
     */
    public String code() {
        return code;
    }

    /**
     * 클라이언트에 공개할 안전한 오류 메시지를 반환한다.
     *
     * @return 클라이언트에 공개할 안전한 오류 메시지
     */
    public String message() {
        return message;
    }
}
