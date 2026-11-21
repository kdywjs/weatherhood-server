package com.weatherhood.today.exception;

import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;

/**
 * 수집 작업의 내부 실패를 나타내는 예외.
 *
 * <p>공급처 자료 부족으로 숨기지 않고 INTERNAL_ERROR로 처리한다.
 * 원인은 내부 진단용으로 보존하며 공개 응답에는 포함하지 않는다.
 */
public class WeatherCollectionException extends ApplicationException {
    /**
     * 수집 내부 실패를 공개 INTERNAL_ERROR로 변환한다.
     *
     * @param cause 공개 응답에 노출하지 않을 내부 원인
     */
    public WeatherCollectionException(Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, null, cause);
    }
}
