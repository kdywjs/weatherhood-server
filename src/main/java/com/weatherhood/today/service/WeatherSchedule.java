package com.weatherhood.today.service;

import com.weatherhood.today.dto.WeatherSource;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 공급처 발표 주기에 따른 확인 시각 계산.
 *
 * <p>반환 시각은 공급 자료가 실제 게시되었다는 보장이 아닌 초기 확인 기준이다.
 * 모든 입력 시각은 한국 현지 시각으로 전달해야 한다.
 */
public final class WeatherSchedule {
    /**
     * 날씨 날짜와 시간 슬롯을 판정할 한국 표준시.
     */
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private WeatherSchedule() {
    }

    /**
     * 현재 시각에서 확인 대상으로 삼을 최신 발표·측정 기준 시각을 계산한다.
     *
     * @param source 자료 종류
     * @param now    한국 현지 시각
     * @return 확인할 자료의 기준 시각; 초단기예보 요청은 30분 표기 사용
     */
    public static OffsetDateTime resolveLatestReleaseTime(WeatherSource source, OffsetDateTime now) {
        OffsetDateTime hour = now.truncatedTo(ChronoUnit.HOURS);
        return switch (source) {
            case OBSERVATION -> now.getMinute() >= 12 ? hour : hour.minusHours(1);
            case SUN -> now.toLocalDate().atStartOfDay(KST).toOffsetDateTime();
            case AIR -> now.getMinute() >= 20 ? hour : hour.minusHours(1);
            case ULTRA -> (now.getMinute() >= 47 ? hour : hour.minusHours(1)).plusMinutes(30);
            case SHORT -> {
                OffsetDateTime candidate = now.minusMinutes(12).truncatedTo(ChronoUnit.HOURS);
                while (candidate.getHour() % 3 != 2) {
                    candidate = candidate.minusHours(1);
                }
                yield candidate;
            }
        };
    }

    /**
     * 다음 발표를 확인하기 시작할 시각을 계산한다.
     *
     * @param source 자료 종류
     * @param now    한국 현지 시각
     * @return 다음 발표 확인 시각; 실제 수집은 이후 요청이 들어올 때 수행
     */
    public static OffsetDateTime calculateNextCheckTime(WeatherSource source, OffsetDateTime now) {
        OffsetDateTime releaseTime = resolveLatestReleaseTime(source, now);
        return switch (source) {
            case OBSERVATION -> releaseTime.plusHours(1).plusMinutes(12);
            case ULTRA -> releaseTime.plusHours(1).plusMinutes(17);
            case SHORT -> releaseTime.plusHours(3).plusMinutes(12);
            case AIR -> releaseTime.plusHours(1).plusMinutes(20);
            case SUN -> releaseTime.plusDays(1);
        };
    }
}
