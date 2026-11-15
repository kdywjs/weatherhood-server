package com.weatherhood.today.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.weatherhood.today.dto.ExactPrecipitation;
import com.weatherhood.today.dto.Precipitation;
import com.weatherhood.today.dto.PrecipitationRange;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.dto.WeatherSource;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class WeatherNormalizerTest {
    private final OffsetDateTime end = OffsetDateTime.parse("2026-11-15T00:00:00+09:00");
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @CsvSource({"0,none", "1,rain", "2,rain_snow", "3,snow", "4,shower", "5,drizzle",
            "6,drizzle_snow_flurries", "7,snow_flurries"})
    void precipitationTypesPreserveMeaningAndRespectSource(int code, String expected) {
        for (WeatherSource source : List.of(WeatherSource.OBSERVATION, WeatherSource.ULTRA, WeatherSource.SHORT)) {
            var items = mapper.readTree("""
                    [{"baseDate":"20261114","baseTime":"2300","fcstDate":"20261115","fcstTime":"0000",
                      "category":"PTY","obsrValue":"%s","fcstValue":"%s"}]
                    """.formatted(code, code));
            var values = WeatherNormalizer.normalizeKmaItems(source, "60,127", items);
            if (source == WeatherSource.SHORT && code > 4) {
                assertThat(values).isEmpty();
            } else {
                assertThat(values).extracting(WeatherDatum::value).containsExactly(expected);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"900", "999", "-900", "-999", "NaN", "Infinity", "", "null", "1..0mm",
            "900mm이상", "-1mm미만", "1mmmm"})
    void missingAndMalformedPrecipitationNeverBecomesAValue(String raw) {
        for (WeatherSource source : List.of(WeatherSource.OBSERVATION, WeatherSource.ULTRA, WeatherSource.SHORT)) {
            assertThat(WeatherNormalizer.parsePrecipitation(source, raw, end.minusHours(1), end)).isNull();
        }
    }

    @Test
    void forecastNoRainDoesNotTurnMissingObservationIntoZero() {
        assertThat(WeatherNormalizer.parsePrecipitation(WeatherSource.SHORT, "-", end.minusHours(1), end))
                .isEqualTo(new ExactPrecipitation(0, end.minusHours(1), end));
        assertThat(WeatherNormalizer.parsePrecipitation(WeatherSource.OBSERVATION, "-", end.minusHours(1), end))
                .isNull();
        assertThat(WeatherNormalizer.parsePrecipitation(WeatherSource.SHORT, null, end.minusHours(1), end)).isNull();
    }

    @Test
    void numericForecastCategoriesAreDifferentFromMeasuredAmounts() {
        assertThat(WeatherNormalizer.parsePrecipitation(WeatherSource.OBSERVATION, "35", end.minusHours(1), end))
                .isEqualTo(new ExactPrecipitation(35, end.minusHours(1), end));
        assertThat(WeatherNormalizer.parsePrecipitation(WeatherSource.SHORT, "35", end.minusHours(1), end))
                .isEqualTo(new PrecipitationRange(30, 50.0, true, false, end.minusHours(1), end));
        assertThat(WeatherNormalizer.parsePrecipitation(WeatherSource.ULTRA, "50", end.minusHours(1), end))
                .isEqualTo(new PrecipitationRange(50, null, true, false, end.minusHours(1), end));
        assertThat(WeatherNormalizer.parsePrecipitation(WeatherSource.SHORT, "0.2", end.minusHours(1), end))
                .isEqualTo(new ExactPrecipitation(0.2, end.minusHours(1), end));
    }

    @Test
    void missingJsonValuesPreserveValidSiblingsAndMidnightPeriod() {
        var items = mapper.readTree("""
                [
                  {"baseDate":"20261114","baseTime":"2300","fcstDate":"20261114","fcstTime":"2400",
                   "category":"PCP","fcstValue":"0"},
                  {"baseDate":"20261114","baseTime":"2300","fcstDate":"20261115","fcstTime":"0100",
                   "category":"PCP","fcstValue":null},
                  {"baseDate":"20261114","baseTime":"2300","fcstDate":"20261115","fcstTime":"0200",
                   "category":"PCP"},
                  {"baseDate":"20261114","baseTime":"2300","fcstDate":"20261115","fcstTime":"0000",
                   "category":"PTY","fcstValue":"8"},
                  {"baseDate":"20261114","baseTime":"2300","fcstDate":"20261115","fcstTime":"0000",
                   "category":"TMP","fcstValue":"-1"}
                ]
                """);
        var values = WeatherNormalizer.normalizeKmaItems(WeatherSource.SHORT, "60,127", items);
        assertThat(values).hasSize(2);
        assertThat(values.getFirst().target()).isEqualTo(end);
        Precipitation amount = (Precipitation) values.getFirst().value();
        assertThat(amount).isEqualTo(new ExactPrecipitation(0, end.minusHours(1), end));
        assertThat(values.getLast().value()).isEqualTo(-1.0);
    }

    @Test
    void observationCannotInventSkyOrProbabilityButUltraForecastIncludesProbability() {
        var items = mapper.readTree("""
                [{"baseDate":"20261115","baseTime":"0000","fcstDate":"20261115","fcstTime":"0100",
                  "category":"POP","obsrValue":"60","fcstValue":"60"},
                 {"baseDate":"20261115","baseTime":"0000","fcstDate":"20261115","fcstTime":"0100",
                  "category":"SKY","obsrValue":"4","fcstValue":"4"}]
                """);
        assertThat(WeatherNormalizer.normalizeKmaItems(WeatherSource.OBSERVATION, "60,127", items)).isEmpty();
        assertThat(WeatherNormalizer.normalizeKmaItems(WeatherSource.ULTRA, "60,127", items))
                .extracting(WeatherDatum::field).containsExactly(WeatherField.PROBABILITY, WeatherField.SKY);
    }
}
