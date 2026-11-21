package com.weatherhood.common.exception;


/**
 * 공개 오류 코드와 안전한 메시지를 전달하는 업무 예외.
 *
 * <p>HTTP 상태와 헤더는 {@link GlobalExceptionHandler}에서 결정한다. 원본 예외를 보존하더라도
 * 응답에는 공개 코드와 메시지만 포함한다.
 */
public class ApplicationException extends RuntimeException {
    /**
     * 공개 업무 오류 식별자.
     */
    private final ErrorCode code;
    /**
     * 다음 시도까지 대기할 초.
     */
    private final Long retryAfterSeconds;

    /**
     * 공개 오류와 선택적 재시도 지연·내부 원인을 보관한다.
     *
     * @param code 공개 오류 식별자
     */
    public ApplicationException(ErrorCode code) {
        this(code, null);
    }

    /**
     * 공개 오류와 선택적 재시도 지연·내부 원인을 보관한다.
     *
     * @param code              공개 오류 식별자
     * @param retryAfterSeconds 다음 재시도까지 기다릴 초, 알 수 없으면 null
     */
    public ApplicationException(ErrorCode code, Long retryAfterSeconds) {
        this(code, retryAfterSeconds, null);
    }

    /**
     * 공개 오류와 선택적 재시도 지연·내부 원인을 보관한다.
     *
     * @param code              공개 오류 식별자
     * @param retryAfterSeconds 다음 재시도까지 기다릴 초, 알 수 없으면 null
     * @param cause             공개 응답에 노출하지 않을 내부 원인
     */
    protected ApplicationException(ErrorCode code, Long retryAfterSeconds, Throwable cause) {
        super(code.message(), cause);
        if (retryAfterSeconds != null && retryAfterSeconds < 0) {
            throw new IllegalArgumentException("Negative retry delay");
        }
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * 클라이언트에 제공할 안정적인 오류 식별자를 반환한다.
     *
     * @return 클라이언트에 제공할 안정적인 오류 식별자
     */
    public ErrorCode code() {
        return code;
    }

    /**
     * 다음 재시도까지 대기할 시간을 반환한다.
     *
     * @return 다음 재시도까지 대기할 시간
     */
    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
