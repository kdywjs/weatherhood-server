package com.weatherhood.today.exception;

/**
 * 공급처 호출 실패의 원인과 재시도 범위를 전달하는 내부 예외.
 *
 * <p>수집 서비스가 부분 응답과 호출 억제를 판단할 때 사용한다.
 * 공급처 URL·원문·인증키가 들어갈 수 있는 원본 예외는 이 예외에 포함하지 않는다.
 */
public class WeatherProviderException extends RuntimeException {
    public enum Reason {
        AUTHENTICATION,
        UNAVAILABLE,
        INVALID_RESPONSE,
        INTERRUPTED
    }

    private final Reason reason;
    private final boolean providerWide;
    private final long retryAfterSeconds;

    public WeatherProviderException(Reason reason, boolean providerWide, long retryAfterSeconds) {
        super("Weather provider request failed: " + reason);
        this.reason = reason;
        this.providerWide = providerWide;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean isAuthenticationFailure() {
        return reason == Reason.AUTHENTICATION;
    }

    public Reason reason() {
        return reason;
    }

    public boolean isProviderWide() {
        return providerWide;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
