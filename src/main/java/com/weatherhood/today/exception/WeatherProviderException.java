package com.weatherhood.today.exception;

/**
 * 공급처 호출 실패의 원인과 재시도 범위를 전달하는 내부 예외.
 *
 * <p>수집 서비스가 부분 응답과 호출 억제를 판단할 때 사용한다.
 * 공급처 URL·원문·인증키가 들어갈 수 있는 원본 예외는 이 예외에 포함하지 않는다.
 */
public class WeatherProviderException extends RuntimeException {
    /**
     * 공급처 실패 원인을 구분하는 내부 분류.
     */
    public enum Reason {
        /**
         * 인증키 또는 할당량 오류.
         */
        AUTHENTICATION,
        /**
         * 통신 실패 또는 공급처 일시 장애.
         */
        UNAVAILABLE,
        /**
         * 예상하지 못한 응답 형식.
         */
        INVALID_RESPONSE,
        /**
         * 수집 작업의 인터럽트.
         */
        INTERRUPTED
    }

    /**
     * 공급처 실패 분류.
     */
    private final Reason reason;
    /**
     * 공급처 전체에 재시도 지연을 적용할지 여부.
     */
    private final boolean providerWide;
    /**
     * 다음 시도까지 대기할 초.
     */
    private final long retryAfterSeconds;

    /**
     * 공급처 실패 분류와 재시도 제한 범위를 보관한다.
     *
     * @param reason            공급처 실패 분류
     * @param providerWide      공급처 전체에 재시도 제한을 적용할지 여부
     * @param retryAfterSeconds 다음 재시도까지 대기할 초
     */
    public WeatherProviderException(Reason reason, boolean providerWide, long retryAfterSeconds) {
        super("Weather provider request failed: " + reason);
        this.reason = reason;
        this.providerWide = providerWide;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * 공급처 인증·할당량 오류로 분류됐는지 확인한다.
     *
     * @return 인증·할당량 실패이면 true
     */
    public boolean isAuthenticationFailure() {
        return reason == Reason.AUTHENTICATION;
    }

    /**
     * 공급처 실패의 내부 분류를 반환한다.
     *
     * @return 공급처 실패의 내부 분류
     */
    public Reason reason() {
        return reason;
    }

    /**
     * 특정 지역이 아닌 공급처 전체의 호출을 제한해야 하는지 반환한다.
     *
     * @return 공급처 전체 제한이 필요하면 true
     */
    public boolean isProviderWide() {
        return providerWide;
    }

    /**
     * 다음 재시도까지 대기할 시간을 반환한다.
     *
     * @return 다음 재시도까지 대기할 시간
     */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
