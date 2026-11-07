package com.weatherhood.weather.presentation;

import com.weatherhood.weather.application.WeatherQueryService;
import com.weatherhood.weather.domain.*;
import java.time.*;
import java.util.*;

public final class WeatherResponse {
    private WeatherResponse() {}

    public record Provenance(String source, OffsetDateTime issuedAt, OffsetDateTime observedAt, OffsetDateTime fetchedAt) {}
    public record Amount(String kind, Double value, Double lowerBound, Double upperBound, Boolean lowerInclusive,
                         Boolean upperInclusive, String intensity, String unit, OffsetDateTime periodStart, OffsetDateTime periodEnd) {}
    public record Point(OffsetDateTime time, int stepMinutes, Double temperatureC, String sky,
                        String precipitationType, Double precipitationProbabilityPercent, Double windSpeedMps,
                        Double humidityPercent, Amount precipitationAmount, String windIntensity, String source,
                        OffsetDateTime issuedAt, OffsetDateTime observedAt, Map<String, Provenance> provenance) {}
    public record Interval(OffsetDateTime from, OffsetDateTime to, int stepMinutes) {}
    public record Missing(OffsetDateTime from, OffsetDateTime to, String field, String reason) {}
    public record Coverage(LocalDate date, boolean timelineAvailable, List<Interval> intervals) {}
    public record Timeline(String provider, String timezone, Grid grid, LocalDate date, OffsetDateTime fetchedAt,
                           String status, List<Missing> missing, Coverage coverage, List<Point> points) {}
    public record Period(OffsetDateTime time, String sky, String precipitationType) {}
    public record Day(LocalDate date, Double temperatureMinC, Double temperatureMaxC, String sky,
                      String precipitationType, boolean timelineAvailable, Map<String, Provenance> provenance,
                      List<Period> periods) {}
    public record Overview(String provider, String timezone, Grid grid, OffsetDateTime fetchedAt, String status,
                           List<Missing> missing, Coverage coverage, Point current, List<Point> hourly, List<Day> daily) {}

    public static Timeline timeline(WeatherQueryService.Result result) {
        var view = result.view();
        return new Timeline("kma", "Asia/Seoul", result.grid(), result.date(), offset(view.fetchedAt()), view.status(),
                missing(view.missing()), coverage(result.date(), view.intervals()), view.points().stream().map(WeatherResponse::point).toList());
    }

    public static Overview overview(WeatherQueryService.OverviewResult result) {
        var view = result.overview();
        return new Overview("kma", "Asia/Seoul", result.grid(), offset(view.fetchedAt()), view.status(),
                missing(view.missing()), coverage(null, view.intervals()), point(view.current()),
                view.hourly().stream().map(WeatherResponse::point).toList(), view.daily().stream().map(d -> new Day(
                        d.date(), d.temperatureMinC(), d.temperatureMaxC(), d.sky(), d.precipitationType(),
                        d.timelineAvailable(), provenance(d.provenance()), d.periods().stream()
                        .map(p -> new Period(offset(p.time()), p.sky(), p.precipitationType())).toList())).toList());
    }

    private static Point point(WeatherTimeline.Point p) {
        if (p == null) return null;
        var v = p.values();
        var amount = v.precipitationAmount();
        Amount rain = amount == null ? null : new Amount(amount.kind(), amount.value(), amount.lowerBound(), amount.upperBound(),
                amount.lowerInclusive(), amount.upperInclusive(), amount.intensity(), amount.unit(),
                offset(amount.periodStart()), offset(amount.periodEnd()));
        return new Point(offset(p.time()), p.stepMinutes(), v.temperatureC(), v.sky(), v.precipitationType(),
                v.precipitationProbabilityPercent(), v.windSpeedMps(), v.humidityPercent(), rain, v.windIntensity(),
                p.source(), offset(p.issuedAt()), offset(p.observedAt()), provenance(p.provenance()));
    }

    private static Map<String, Provenance> provenance(Map<String, WeatherTimeline.Provenance> source) {
        Map<String, Provenance> result = new TreeMap<>();
        source.forEach((key, p) -> result.put(key, new Provenance(p.source(), offset(p.issuedAt()),
                offset(p.observedAt()), offset(p.fetchedAt()))));
        return result;
    }
    private static Coverage coverage(LocalDate date, List<WeatherTimeline.Interval> intervals) {
        return new Coverage(date, !intervals.isEmpty(), intervals.stream()
                .map(i -> new Interval(offset(i.from()), offset(i.to()), i.stepMinutes())).toList());
    }
    private static List<Missing> missing(List<WeatherTimeline.Missing> missing) {
        return missing.stream().map(m -> new Missing(offset(m.from()), offset(m.to()), m.field(), m.reason())).toList();
    }
    private static OffsetDateTime offset(ZonedDateTime time) { return time == null ? null : time.toOffsetDateTime(); }
}
