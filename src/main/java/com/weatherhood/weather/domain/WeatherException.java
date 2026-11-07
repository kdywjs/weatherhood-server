package com.weatherhood.weather.domain;

public final class WeatherException extends RuntimeException {
    public enum Code { INVALID_REQUEST, UNSUPPORTED_LOCATION, UNSUPPORTED_DATE, WEATHER_UNAVAILABLE }
    private final Code code;

    public WeatherException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }

    public static WeatherException unavailable() {
        return new WeatherException(Code.WEATHER_UNAVAILABLE, "날씨 자료를 가져올 수 없습니다. 잠시 후 다시 시도해주세요.");
    }
}
