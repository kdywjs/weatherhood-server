package com.weatherhood.common.exception;

import com.weatherhood.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;


/**
 * 업무·입력 검증·예상하지 못한 오류를 공통 HTTP 오류 응답으로 변환하는 처리기.
 *
 * <p>예외 메시지, 공급처 응답 본문과 인증키를 노출하지 않는다. 업무 코드별 HTTP 상태를 유지하며
 * 알려진 재시도 대기 시간이 있는 503 응답에만 Retry-After 헤더를 추가한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * Spring MVC에 등록할 공통 예외 처리기를 생성한다.
     */
    public GlobalExceptionHandler() {
    }

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 업무 예외를 정의된 HTTP 상태와 공통 오류 응답으로 변환한다.
     *
     * @param exception 공통 오류 응답으로 변환할 예외
     * @return HTTP 상태와 공통 응답 객체
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> application(ApplicationException exception) {
        return response(exception.code(), exception.retryAfterSeconds());
    }

    /**
     * 입력 검증 실패를 400 오류 응답으로 변환한다.
     *
     * @param exception 공통 오류 응답으로 변환할 예외
     * @return HTTP 상태와 공통 응답 객체
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest(Exception exception) {
        return response(ErrorCode.INVALID_REQUEST, null);
    }

    /**
     * 지원하지 않는 HTTP 메서드를 405 응답과 Allow 헤더로 변환한다.
     *
     * @param exception 공통 오류 응답으로 변환할 예외
     * @return HTTP 상태와 공통 응답 객체
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        HttpHeaders headers = new HttpHeaders();
        if (exception.getSupportedHttpMethods() != null) {
            headers.setAllow(exception.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers)
                .body(ApiResponse.failure(ErrorCode.METHOD_NOT_ALLOWED.code(), ErrorCode.METHOD_NOT_ALLOWED.message(),
                        null));
    }

    /**
     * 지원하지 않는 요청 본문 형식을 415 오류로 변환한다.
     *
     * @param exception 공통 오류 응답으로 변환할 예외
     * @return HTTP 상태와 공통 응답 객체
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> unsupportedMedia(Exception exception) {
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
    }

    /**
     * 지원하지 않는 응답 형식 요청을 406 오류로 변환한다.
     *
     * @param exception 공통 오류 응답으로 변환할 예외
     * @return HTTP 상태와 공통 응답 객체
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> notAcceptable(Exception exception) {
        return response(ErrorCode.NOT_ACCEPTABLE, null);
    }

    /**
     * 일치하는 경로나 리소스가 없는 경우 404 오류로 변환한다.
     *
     * @param exception 공통 오류 응답으로 변환할 예외
     * @return HTTP 상태와 공통 응답 객체
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> notFound(Exception exception) {
        return response(ErrorCode.NOT_FOUND, null);
    }

    /**
     * 예상하지 못한 실패를 원문 노출 없이 500 오류로 변환한다.
     *
     * @param exception 공통 오류 응답으로 변환할 예외
     * @return HTTP 상태와 공통 응답 객체
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        // Messages, causes, query strings and provider bodies may contain credentials.
        log.error("Unhandled request failure: type={}", exception.getClass().getName());
        return response(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Void>> response(ErrorCode code, Long retryAfterSeconds) {
        HttpStatus status = switch (code) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case REGION_NOT_FOUND, NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNSUPPORTED_REGION, LOCATION_NOT_FOUND -> HttpStatus.UNPROCESSABLE_CONTENT;
            case WEATHER_UNAVAILABLE, WEEKLY_WEATHER_UNAVAILABLE, AUTH_UNAVAILABLE, LOCATION_UNAVAILABLE,
                 REGION_MAPPING_UNAVAILABLE ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case NOT_ACCEPTABLE -> HttpStatus.NOT_ACCEPTABLE;
            case UNAUTHENTICATED, INVALID_APPLE_CREDENTIAL -> HttpStatus.UNAUTHORIZED;
            case SAVED_REGION_LIMIT, REGION_ORDER_CONFLICT, DEFAULT_REGION_NOT_SAVED -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        var response = ResponseEntity.status(status);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE && retryAfterSeconds != null) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
        return response.body(ApiResponse.failure(code.code(), code.message(), retryAfterSeconds));
    }
}
