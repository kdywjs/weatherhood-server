package com.weatherhood.weather.presentation;

import com.weatherhood.weather.domain.WeatherException;
import org.springframework.http.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class WeatherErrorHandler {
    public record Detail(String code, String message, Integer retryAfterSeconds) {}
    public record ErrorResponse(Detail error) {}

    @ExceptionHandler(WeatherException.class)
    public ResponseEntity<ErrorResponse> weather(WeatherException error) {
        HttpStatus status = switch (error.code()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNSUPPORTED_LOCATION, UNSUPPORTED_DATE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case WEATHER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        Integer retry = status == HttpStatus.SERVICE_UNAVAILABLE ? 60 : null;
        var builder = ResponseEntity.status(status);
        if (retry != null) builder.header(HttpHeaders.RETRY_AFTER, retry.toString());
        return builder.body(new ErrorResponse(new Detail(error.code().name(), error.getMessage(), retry)));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> input(Exception ignored) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                new Detail("INVALID_REQUEST", "좌표와 날짜 형식을 확인해주세요.", null)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ignored) {
        return ResponseEntity.internalServerError().body(new ErrorResponse(
                new Detail("INTERNAL_ERROR", "요청을 처리하지 못했습니다.", null)));
    }
}
