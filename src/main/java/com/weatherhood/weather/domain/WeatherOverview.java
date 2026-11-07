package com.weatherhood.weather.domain;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public record WeatherOverview(WeatherTimeline.Point current, List<WeatherTimeline.Point> hourly,
                              List<Day> daily, List<WeatherTimeline.Missing> missing,
                              List<WeatherTimeline.Interval> intervals, ZonedDateTime fetchedAt, String status) {
    public record Period(ZonedDateTime time, String sky, String precipitationType) {}
    public record Day(LocalDate date, Double temperatureMinC, Double temperatureMaxC,
                      String sky, String precipitationType, boolean timelineAvailable,
                      Map<String, WeatherTimeline.Provenance> provenance, List<Period> periods) {}

    public static WeatherOverview from(WeatherTimeline.View view, ZonedDateTime now) {
        ZonedDateTime from = now.truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime to = now.plusHours(24);
        var hourly = view.points().stream().filter(p -> !p.time().isBefore(from) && p.time().isBefore(to)).toList();
        var missing = new ArrayList<WeatherTimeline.Missing>();
        for (var gap : view.missing()) {
            if (!gap.to().isAfter(from) || !gap.from().isBefore(to)) continue;
            missing.add(new WeatherTimeline.Missing(gap.from().isBefore(from) ? from : gap.from(),
                    gap.to().isAfter(to) ? to : gap.to(), gap.field(), gap.reason()));
        }
        SortedMap<LocalDate, List<WeatherTimeline.Point>> grouped = new TreeMap<>();
        for (var point : view.points()) {
            if (!point.time().toLocalDate().isBefore(now.toLocalDate()))
                grouped.computeIfAbsent(point.time().toLocalDate(), ignored -> new ArrayList<>()).add(point);
        }
        List<Day> daily = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            var provenance = new LinkedHashMap<String, WeatherTimeline.Provenance>();
            var low = dailyValue(entry.getValue(), "temperatureMinC");
            var high = dailyValue(entry.getValue(), "temperatureMaxC");
            if (low != null) provenance.put("temperatureMinC", low.provenance().get("temperatureMinC"));
            if (high != null) provenance.put("temperatureMaxC", high.provenance().get("temperatureMaxC"));
            daily.add(new Day(entry.getKey(), low == null ? null : low.values().temperatureMinC(),
                    high == null ? null : high.values().temperatureMaxC(), null, null, true,
                    Map.copyOf(provenance), entry.getValue().stream()
                    .map(p -> new Period(p.time(), p.values().sky(), p.values().precipitationType())).toList()));
            ZonedDateTime start = entry.getKey().atStartOfDay(now.getZone());
            for (String field : List.of("temperatureMinC", "temperatureMaxC", "sky", "precipitationType")) {
                if (!provenance.containsKey(field)) missing.add(new WeatherTimeline.Missing(start, start.plusDays(1),
                        "daily." + entry.getKey() + "." + field, field.equals("sky") || field.equals("precipitationType")
                        ? "aggregation_not_defined" : "source_unavailable"));
            }
        }
        var current = new WeatherTimeline().current(view, now);
        if (current == null) missing.add(new WeatherTimeline.Missing(from, to, "current", "source_unavailable"));
        String status = (current != null && current.stale()) || hourly.stream().anyMatch(WeatherTimeline.Point::stale)
                ? "stale" : missing.isEmpty() ? "complete" : "partial";
        return new WeatherOverview(current, hourly, List.copyOf(daily), List.copyOf(missing),
                view.intervals().stream().filter(i -> !i.from().toLocalDate().isBefore(now.toLocalDate())).toList(),
                view.fetchedAt(), status);
    }

    private static WeatherTimeline.Point dailyValue(List<WeatherTimeline.Point> points, String field) {
        return points.stream().filter(p -> p.provenance().containsKey(field))
                .max(Comparator.comparing(p -> p.provenance().get(field).issuedAt(),
                        Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
    }
}
