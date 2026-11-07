package com.weatherhood.weather.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WeatherDomainTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Grid GRID = new Grid(60, 127);
    private final WeatherTimeline timeline = new WeatherTimeline();
    private static ZonedDateTime at(String value) { return LocalDateTime.parse(value).atZone(KST); }
    private static WeatherValues values(Double temp, String sky, String rain) {
        return new WeatherValues(temp, sky, rain, 30.0, 2.0, 60.0, null, null, null, null);
    }
    private static WeatherBatch batch(WeatherSource source, String base, String time, WeatherValues values) {
        return new WeatherBatch(GRID, source, at(base), at("2026-11-02T15:12:00"),
                List.of(new WeatherBatch.Sample(at(time), 60, values)));
    }

    @Test void WTH_001_projection_and_non_finite_inputs() {
        assertThat(Grid.from(37.5665, 126.978)).isEqualTo(GRID);
        for (double latitude : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 91, -91})
            assertThatThrownBy(() -> Grid.from(latitude, 126)).isInstanceOf(WeatherException.class);
    }

    @Test void WTH_003_publication_rolls_over_midnight_and_month() {
        assertThat(WeatherSource.OBSERVATION.latestPublication(at("2026-11-01T00:05:00")))
                .isEqualTo(at("2026-10-31T23:00:00"));
        assertThat(WeatherSource.OBSERVATION.latestPublication(at("2026-11-01T00:10:00")))
                .isEqualTo(at("2026-11-01T00:00:00"));
        assertThat(WeatherSource.ULTRA_SHORT_TERM_FORECAST.latestPublication(at("2026-11-01T00:44:00")))
                .isEqualTo(at("2026-10-31T23:30:00"));
        assertThat(WeatherSource.ULTRA_SHORT_TERM_FORECAST.latestPublication(at("2026-11-01T00:45:00")))
                .isEqualTo(at("2026-11-01T00:30:00"));
        assertThat(WeatherSource.SHORT_TERM_FORECAST.latestPublication(at("2026-11-01T02:09:00")))
                .isEqualTo(at("2026-10-31T23:00:00"));
        assertThat(WeatherSource.SHORT_TERM_FORECAST.latestPublication(at("2026-11-01T02:10:00")))
                .isEqualTo(at("2026-11-01T02:00:00"));
    }

    @Test void WTH_007_observation_and_forecast_have_field_provenance() {
        var observation = batch(WeatherSource.OBSERVATION, "2026-11-02T10:00:00", "2026-11-02T10:00:00", values(12.0, null, "none"));
        var forecast = batch(WeatherSource.SHORT_TERM_FORECAST, "2026-11-02T08:00:00", "2026-11-02T10:00:00", values(15.0, "cloudy", "rain"));
        var result = timeline.assemble(List.of(forecast, observation), at("2026-11-02T10:00:00"),
                at("2026-11-02T11:00:00"), at("2026-11-02T15:12:00"), Duration.ZERO);
        var point = result.points().getFirst();
        assertThat(point.values().temperatureC()).isEqualTo(12);
        assertThat(point.values().sky()).isEqualTo("cloudy");
        assertThat(point.values().precipitationType()).isEqualTo("none");
        assertThat(point.source()).isEqualTo("mixed");
        assertThat(point.provenance().get("temperatureC").observedAt()).isEqualTo(observation.publishedAt());
        assertThat(point.provenance().get("sky").issuedAt()).isEqualTo(forecast.publishedAt());
    }

    @Test void WTH_008_later_forecasts_and_future_observations_are_not_history() {
        var later = batch(WeatherSource.SHORT_TERM_FORECAST, "2026-11-02T14:00:00", "2026-11-02T09:00:00", values(12.0, "clear", "none"));
        var future = batch(WeatherSource.OBSERVATION, "2026-11-02T16:00:00", "2026-11-02T16:00:00", values(12.0, null, "none"));
        var view = timeline.assemble(List.of(later, future), at("2026-11-02T00:00:00"),
                at("2026-11-03T00:00:00"), at("2026-11-02T15:12:00"), Duration.ZERO);
        assertThat(view.points()).isEmpty();
    }

    @Test void WTH_009_does_not_extend_a_point_across_a_missing_hour() {
        var first = batch(WeatherSource.OBSERVATION, "2026-11-02T00:00:00", "2026-11-02T00:00:00", values(10.0, null, "none"));
        var third = batch(WeatherSource.OBSERVATION, "2026-11-02T02:00:00", "2026-11-02T02:00:00", values(12.0, null, "none"));
        var view = timeline.assemble(List.of(first, third), at("2026-11-02T00:00:00"),
                at("2026-11-02T03:00:00"), at("2026-11-02T15:12:00"), Duration.ZERO);
        assertThat(view.missing()).anyMatch(m -> m.field() == null && m.from().equals(at("2026-11-02T01:00:00"))
                && m.to().equals(at("2026-11-02T02:00:00")));
        assertThat(view.intervals().getFirst().to()).isEqualTo(at("2026-11-02T01:00:00"));
    }

    @Test void WTH_010_current_prefers_latest_published_observation_before_provider_rollover() {
        var observed = batch(WeatherSource.OBSERVATION, "2026-11-02T14:00:00", "2026-11-02T14:00:00", values(11.0, null, "none"));
        var future = batch(WeatherSource.SHORT_TERM_FORECAST, "2026-11-02T14:00:00", "2026-11-02T15:00:00", values(15.0, "clear", "none"));
        var now = at("2026-11-02T15:05:00");
        var view = timeline.assemble(List.of(observed, future), now.toLocalDate().atStartOfDay(KST), now.plusDays(1), now, Duration.ZERO);
        assertThat(timeline.current(view, now).values().temperatureC()).isEqualTo(11);
    }

    @Test void WTH_011_stale_tolerance_limits_actual_publication_age() {
        var old = batch(WeatherSource.SHORT_TERM_FORECAST, "2026-11-02T11:00:00", "2026-11-02T16:00:00", values(15.0, "clear", "none"));
        var from = at("2026-11-02T16:00:00"); var now = at("2026-11-02T15:12:00");
        assertThat(timeline.assemble(List.of(old), from, from.plusHours(1), now, Duration.ZERO).points()).isEmpty();
        assertThat(timeline.assemble(List.of(old), from, from.plusHours(1), now, Duration.ofHours(2)).points()).isEmpty();
        assertThat(timeline.assemble(List.of(old), from, from.plusHours(1), now, Duration.ofHours(5)).status()).isEqualTo("stale");
    }

    @Test void WTH_010_midnight_uses_previous_observation_without_adding_yesterday_to_daily_list() {
        var observed = batch(WeatherSource.OBSERVATION, "2026-11-01T23:00:00", "2026-11-01T23:00:00", values(11.0, null, "none"));
        var forecast = batch(WeatherSource.SHORT_TERM_FORECAST, "2026-11-01T23:00:00", "2026-11-02T00:00:00", values(15.0, "clear", "none"));
        var now = at("2026-11-02T00:05:00");
        var view = timeline.assemble(List.of(observed, forecast), now.toLocalDate().atStartOfDay(KST).minusHours(1),
                now.plusDays(1), now, Duration.ZERO);
        var overview = WeatherOverview.from(view, now);
        assertThat(overview.current().values().temperatureC()).isEqualTo(11);
        assertThat(overview.daily()).allMatch(day -> !day.date().isBefore(now.toLocalDate()));
    }

    @Test void WTH_020_summary_does_not_invent_daily_extremes_or_representative_weather() {
        var sample = batch(WeatherSource.SHORT_TERM_FORECAST, "2026-11-02T14:00:00", "2026-11-02T16:00:00", values(15.0, "clear", "none"));
        var now = at("2026-11-02T15:12:00");
        var view = timeline.assemble(List.of(sample), now.toLocalDate().atStartOfDay(KST), now.plusDays(5), now, Duration.ZERO);
        var overview = WeatherOverview.from(view, now);
        assertThat(overview.daily().getFirst().temperatureMinC()).isNull();
        assertThat(overview.daily().getFirst().temperatureMaxC()).isNull();
        assertThat(overview.daily().getFirst().sky()).isNull();
        assertThat(overview.hourly()).hasSize(1);
    }
}
