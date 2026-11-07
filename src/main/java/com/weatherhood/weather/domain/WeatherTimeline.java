package com.weatherhood.weather.domain;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

/** Selects usable facts; it neither calls a provider nor invents absent weather. */
public final class WeatherTimeline {
    public record Provenance(String source, ZonedDateTime issuedAt, ZonedDateTime observedAt,
                             ZonedDateTime fetchedAt) {}
    public record Point(ZonedDateTime time, int stepMinutes, WeatherValues values, String source,
                        ZonedDateTime issuedAt, ZonedDateTime observedAt,
                        Map<String, Provenance> provenance, boolean stale) {}
    public record Missing(ZonedDateTime from, ZonedDateTime to, String field, String reason) {}
    public record Interval(ZonedDateTime from, ZonedDateTime to, int stepMinutes) {}
    public record View(List<Point> points, List<Missing> missing, List<Interval> intervals,
                       ZonedDateTime fetchedAt, String status) {}

    private record Candidate(WeatherBatch batch, WeatherBatch.Sample sample, boolean stale) {
        Provenance provenance() {
            boolean observed = batch.source() == WeatherSource.OBSERVATION;
            return new Provenance(batch.source().name().toLowerCase(Locale.ROOT),
                    observed ? null : batch.publishedAt(), observed ? sample.time() : null, batch.fetchedAt());
        }
    }

    public View assemble(List<WeatherBatch> batches, ZonedDateTime from, ZonedDateTime to,
                         ZonedDateTime now, Duration staleTolerance) {
        SortedMap<ZonedDateTime, List<Candidate>> candidates = new TreeMap<>();
        for (WeatherBatch batch : batches) {
            if (batch.publishedAt().isAfter(now)) continue;
            for (WeatherBatch.Sample sample : batch.samples()) {
                if (sample.time().isBefore(from) || !sample.time().isBefore(to)) continue;
                if (batch.source() == WeatherSource.OBSERVATION && sample.time().isAfter(now)) continue;
                if (batch.source() != WeatherSource.OBSERVATION && batch.publishedAt().isAfter(sample.time())) continue;
                boolean stale = batch.source().obsolete(batch.publishedAt(), sample.time(), now, Duration.ZERO);
                if (stale && (staleTolerance.isZero() || batch.publishedAt().plus(staleTolerance).isBefore(now))) continue;
                candidates.computeIfAbsent(sample.time(), ignored -> new ArrayList<>()).add(new Candidate(batch, sample, stale));
            }
        }
        List<Point> points = new ArrayList<>();
        List<Missing> missing = new ArrayList<>();
        List<Interval> intervals = new ArrayList<>();
        ZonedDateTime cursor = from;
        for (var entry : candidates.entrySet()) {
            List<Candidate> rows = entry.getValue();
            rows.sort(Comparator.comparingInt((Candidate c) -> c.batch().source().ordinal())
                    .thenComparing(c -> c.batch().publishedAt(), Comparator.reverseOrder()));
            Point point = choose(entry.getKey(), rows);
            if (point.values().temperatureC() == null) continue;
            points.add(point);
            ZonedDateTime end = point.time().plusMinutes(point.stepMinutes());
            if (end.isAfter(to)) end = to;
            if (point.time().isAfter(cursor)) missing.add(new Missing(cursor, point.time(), null, "source_unavailable"));
            if (end.isAfter(cursor)) cursor = end;
            intervals.add(new Interval(point.time(), end, point.stepMinutes()));
            for (String field : List.of("sky", "precipitationType", "windSpeedMps", "humidityPercent",
                    "precipitationProbabilityPercent", "precipitationAmount")) {
                if (!point.provenance().containsKey(field))
                    missing.add(new Missing(point.time(), end, field, "source_unavailable"));
            }
        }
        if (cursor.isBefore(to)) missing.add(new Missing(cursor, to, null, "source_unavailable"));
        ZonedDateTime fetchedAt = points.stream().flatMap(p -> p.provenance().values().stream())
                .map(Provenance::fetchedAt).max(ZonedDateTime::compareTo).orElse(null);
        String status = points.stream().anyMatch(Point::stale) ? "stale" : missing.isEmpty() ? "complete" : "partial";
        return new View(List.copyOf(points), List.copyOf(missing), List.copyOf(intervals), fetchedAt, status);
    }

    private Point choose(ZonedDateTime time, List<Candidate> rows) {
        Map<String, Provenance> provenance = new LinkedHashMap<>();
        Map<String, Candidate> owners = new HashMap<>();
        WeatherValues values = new WeatherValues(
                select(rows, "temperatureC", WeatherValues::temperatureC, provenance, owners),
                select(rows, "sky", WeatherValues::sky, provenance, owners),
                select(rows, "precipitationType", WeatherValues::precipitationType, provenance, owners),
                select(rows, "precipitationProbabilityPercent", WeatherValues::precipitationProbabilityPercent, provenance, owners),
                select(rows, "windSpeedMps", WeatherValues::windSpeedMps, provenance, owners),
                select(rows, "humidityPercent", WeatherValues::humidityPercent, provenance, owners),
                select(rows, "precipitationAmount", WeatherValues::precipitationAmount, provenance, owners),
                select(rows, "temperatureMinC", WeatherValues::temperatureMinC, provenance, owners),
                select(rows, "temperatureMaxC", WeatherValues::temperatureMaxC, provenance, owners),
                select(rows, "windIntensity", WeatherValues::windIntensity, provenance, owners));
        Set<Provenance> origins = new HashSet<>(provenance.values());
        Provenance origin = origins.size() == 1 ? origins.iterator().next() : null;
        int step = owners.values().stream().mapToInt(c -> c.sample().stepMinutes()).min().orElse(60);
        boolean stale = owners.values().stream().anyMatch(Candidate::stale);
        return new Point(time, step, values, origin == null ? "mixed" : origin.source(),
                origin == null ? null : origin.issuedAt(), origin == null ? null : origin.observedAt(),
                Map.copyOf(provenance), stale);
    }

    private static <T> T select(List<Candidate> candidates, String field, Function<WeatherValues, T> getter,
                                Map<String, Provenance> provenance, Map<String, Candidate> owners) {
        for (Candidate candidate : candidates) {
            T value = getter.apply(candidate.sample().values());
            if (value != null) {
                provenance.put(field, candidate.provenance());
                owners.put(field, candidate);
                return value;
            }
        }
        return null;
    }

    public Point current(View view, ZonedDateTime now) {
        ZonedDateTime expectedObservation = WeatherSource.OBSERVATION.latestPublication(now);
        Point observed = view.points().stream().filter(p -> p.time().equals(expectedObservation))
                .filter(p -> "observation".equals(p.provenance().get("temperatureC").source()))
                .findFirst().orElse(null);
        if (observed != null) return observed;
        Point currentHour = view.points().stream().filter(p -> p.time().equals(now.truncatedTo(ChronoUnit.HOURS)))
                .findFirst().orElse(null);
        return currentHour;
    }
}
