package com.weatherhood.weather.domain;

import java.time.ZonedDateTime;
import java.util.List;

public record WeatherBatch(Grid grid, WeatherSource source, ZonedDateTime publishedAt,
                           ZonedDateTime fetchedAt, List<Sample> samples) {
    public WeatherBatch {
        publishedAt = publishedAt.withZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"));
        fetchedAt = fetchedAt.withZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"));
        samples = List.copyOf(samples);
    }
    public record Sample(ZonedDateTime time, int stepMinutes, WeatherValues values) {
        public Sample { time = time.withZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")); }
    }
}
