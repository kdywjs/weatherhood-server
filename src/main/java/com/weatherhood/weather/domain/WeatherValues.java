package com.weatherhood.weather.domain;

import java.time.ZonedDateTime;

public record WeatherValues(Double temperatureC, String sky, String precipitationType,
                            Double precipitationProbabilityPercent, Double windSpeedMps,
                            Double humidityPercent, Precipitation precipitationAmount,
                            Double temperatureMinC, Double temperatureMaxC, String windIntensity) {
    public record Precipitation(String kind, Double value, Double lowerBound, Double upperBound,
                                Boolean lowerInclusive, Boolean upperInclusive, String intensity,
                                String unit, ZonedDateTime periodStart, ZonedDateTime periodEnd) {
        public Precipitation {
            var korea = java.time.ZoneId.of("Asia/Seoul");
            if (periodStart != null) periodStart = periodStart.withZoneSameInstant(korea);
            if (periodEnd != null) periodEnd = periodEnd.withZoneSameInstant(korea);
        }
    }

    public boolean hasWeather() {
        return temperatureC != null || sky != null || precipitationType != null;
    }
}
