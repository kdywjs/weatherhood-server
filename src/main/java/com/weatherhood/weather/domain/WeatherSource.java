package com.weatherhood.weather.domain;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public enum WeatherSource {
    OBSERVATION("getUltraSrtNcst"),
    ULTRA_SHORT_TERM_FORECAST("getUltraSrtFcst"),
    SHORT_TERM_FORECAST("getVilageFcst");

    private final String operation;
    WeatherSource(String operation) { this.operation = operation; }
    public String operation() { return operation; }

    public ZonedDateTime latestPublication(ZonedDateTime now) {
        if (this == OBSERVATION) return now.minusMinutes(10).truncatedTo(ChronoUnit.HOURS);
        if (this == ULTRA_SHORT_TERM_FORECAST) {
            ZonedDateTime ready = now.minusMinutes(15);
            ZonedDateTime base = ready.withMinute(30).withSecond(0).withNano(0);
            return base.isAfter(ready) ? base.minusHours(1) : base;
        }
        ZonedDateTime ready = now.minusMinutes(10);
        ZonedDateTime base = ready.withHour(23).truncatedTo(ChronoUnit.HOURS);
        while (base.isAfter(ready)) base = base.minusHours(3);
        return base;
    }

    public int stepMinutes(ZonedDateTime issuedAt, ZonedDateTime validAt) {
        if (this != SHORT_TERM_FORECAST) return 60;
        int days = issuedAt.getHour() < 17 ? 3 : 4;
        return !validAt.toLocalDate().isBefore(issuedAt.toLocalDate().plusDays(days)) ? 180 : 60;
    }

    public boolean obsolete(ZonedDateTime publication, ZonedDateTime target, ZonedDateTime now, Duration tolerance) {
        if (target.isBefore(now.truncatedTo(ChronoUnit.HOURS))) return false;
        ZonedDateTime expected = latestPublication(now);
        return publication.isBefore(expected) && now.isAfter(expected.plus(tolerance));
    }
}
