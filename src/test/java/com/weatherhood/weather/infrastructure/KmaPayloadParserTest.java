package com.weatherhood.weather.infrastructure;

import static org.assertj.core.api.Assertions.*;
import com.weatherhood.weather.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KmaPayloadParserTest {
    private final KmaPayloadParser parser = new KmaPayloadParser(JsonMapper.builder().findAndAddModules().build());
    private static final ZonedDateTime BASE = ZonedDateTime.of(2026, 11, 2, 14, 0, 0, 0, ZoneId.of("Asia/Seoul"));

    private Map<?, ?> row(String category, String value, String day, String hour) {
        return Map.of("nx", 60, "ny", 127, "baseDate", "20261102", "baseTime", "1400",
                "fcstDate", day, "fcstTime", hour, "category", category, "fcstValue", value, "obsrValue", value);
    }

    @Test void WTH_004_body_errors_and_wrong_grid_are_rejected() {
        assertThatThrownBy(() -> parser.page("{\"response\":{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"private-key\"}}}"))
                .isInstanceOf(WeatherException.class).hasMessageNotContaining("private-key");
        assertThatThrownBy(() -> parser.page("{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":0}}}"))
                .isInstanceOf(WeatherException.class);
        assertThatThrownBy(() -> parser.batch(new Grid(61, 127), WeatherSource.SHORT_TERM_FORECAST,
                BASE, BASE, List.of(row("TMP", "12.3", "20261102", "1600"))))
                .isInstanceOf(WeatherException.class);
    }

    @Test void WTH_005_observation_missing_sky_does_not_mean_clear_and_unknown_values_remain_null() {
        var observation = parser.batch(new Grid(60, 127), WeatherSource.OBSERVATION, BASE, BASE,
                List.of(row("T1H", "12.3", "20261102", "1400"), row("PTY", "99", "20261102", "1400"),
                        row("WSD", "-999", "20261102", "1400"), row("REH", "NaN", "20261102", "1400")));
        var values = observation.samples().getFirst().values();
        assertThat(values.sky()).isNull();
        assertThat(values.precipitationType()).isNull();
        assertThat(values.windSpeedMps()).isNull();
        assertThat(values.humidityPercent()).isNull();
    }

    @Test void WTH_005_precipitation_codes_depend_on_source() {
        for (WeatherSource source : List.of(WeatherSource.OBSERVATION, WeatherSource.SHORT_TERM_FORECAST)) {
            var batch = parser.batch(new Grid(60, 127), source, BASE, BASE,
                    List.of(row("T1H", "12", "20261102", "1600"), row("PTY", "6", "20261102", "1600")));
            assertThat(batch.samples().getFirst().values().precipitationType())
                    .isEqualTo(source == WeatherSource.OBSERVATION ? "drizzle_snow_flurries" : null);
        }
    }

    @Test void WTH_006_rain_amounts_preserve_bounds_and_periods() {
        var below = KmaPayloadParser.precipitation("1mm 미만", false, BASE);
        assertThat(below.kind()).isEqualTo("less_than");
        assertThat(below.value()).isNull();
        assertThat(below.upperBound()).isEqualTo(1);
        assertThat(below.periodStart()).isEqualTo(BASE.minusHours(1));
        var range = KmaPayloadParser.precipitation("30.0~50.0mm", false, BASE);
        assertThat(range.lowerBound()).isEqualTo(30);
        assertThat(range.upperBound()).isEqualTo(50);
        assertThat(KmaPayloadParser.precipitation("30", false, BASE).kind()).isEqualTo("range");
        assertThat(KmaPayloadParser.precipitation("강수없음", false, BASE).value()).isZero();
        assertThat(KmaPayloadParser.precipitation("-999", false, BASE)).isNull();
        for (String invalid : List.of("-999mm 미만", "NaNmm 이상", "50~30mm", "Infinity~50mm"))
            assertThat(KmaPayloadParser.precipitation(invalid, false, BASE)).isNull();
    }

    @Test void WTH_006_extended_forecast_does_not_convert_intensity_codes_to_numeric_weather() {
        var batch = parser.batch(new Grid(60, 127), WeatherSource.SHORT_TERM_FORECAST, BASE, BASE,
                List.of(row("TMP", "15", "20261105", "1200"), row("WSD", "2*", "20261105", "1200"),
                        row("PCP", "2*", "20261105", "1200")));
        var sample = batch.samples().getFirst();
        assertThat(sample.stepMinutes()).isEqualTo(180);
        assertThat(sample.values().windSpeedMps()).isNull();
        assertThat(sample.values().windIntensity()).isEqualTo("moderate");
        assertThat(sample.values().precipitationAmount().value()).isNull();
        assertThat(sample.values().precipitationAmount().intensity()).isEqualTo("moderate");
    }
}
