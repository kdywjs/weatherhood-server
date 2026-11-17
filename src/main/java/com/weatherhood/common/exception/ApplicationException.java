package com.weatherhood.common.exception;


/**
 * 공개 오류 코드와 안전한 메시지를 전달하는 업무 예외.
 *
 * <p>HTTP 상태와 헤더는 {@link GlobalExceptionHandler}에서 결정한다. 원본 예외를 보존하더라도
 * 응답에는 공개 코드와 메시지만 포함한다.
 */
public class ApplicationException extends RuntimeException {
    private final ErrorCode code;
    private final Long retryAfterSeconds;

    public ApplicationException(ErrorCode code) {
        this(code, null);
    }

    public ApplicationException(ErrorCode code, Long retryAfterSeconds) {
        this(code, retryAfterSeconds, null);
    }

    protected ApplicationException(ErrorCode code, Long retryAfterSeconds, Throwable cause) {
        super(code.message(), cause);
        if (retryAfterSeconds != null && retryAfterSeconds < 0) {
            throw new IllegalArgumentException("Negative retry delay");
        }
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode code() {
        return code;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
