package com.weatherhood.today;

import static org.assertj.core.api.Assertions.assertThat;

import com.weatherhood.today.client.WeatherNormalizer;
import com.weatherhood.today.dto.ExactPrecipitation;
import com.weatherhood.today.dto.PollutantReading;
import com.weatherhood.today.dto.PrecipitationRange;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.entity.WeatherRegion;
import com.weatherhood.today.service.WeatherComposer;
import com.weatherhood.today.service.WeatherSchedule;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WeatherPolicyTest {
    final OffsetDateTime now = OffsetDateTime.parse("2026-11-15T12:00:00+09:00");
    final WeatherRegion region = new WeatherRegion("test", "서울 중구 명동", true, 60, 127, "중구", "중구");
    final WeatherComposer composer = new WeatherComposer();

    WeatherDatum d(WeatherSource s, WeatherField f, int hour, int issue, double v) {
        return new WeatherDatum(s, "60,127", f, now.withHour(hour), now.withHour(issue), v);
    }

    @Test
    void missingSlotsAndRealZeroAreDifferent() {
        var response = composer.composeTodayWeather(region, now.toLocalDate(), now,
                List.of(d(WeatherSource.OBSERVATION, WeatherField.TEMPERATURE, 0, 0, 0)));
        assertThat(response.hourly()).hasSize(24);
        assertThat(response.hourly().get("00:00").temperatureC()).isZero();
        assertThat(response.hourly().get("01:00")).isNull();
        assertThat(response.current().temperatureC()).isNull();
        assertThat(WeatherComposer.hasNoWeatherData(response)).isFalse();
    }

    @Test
    void observationWinsAndLaterForecastCannotRewritePast() {
        var result = composer.composeTodayWeather(region, now.toLocalDate(), now,
                List.of(d(WeatherSource.OBSERVATION, WeatherField.TEMPERATURE, 11, 11, 10),
                        d(WeatherSource.ULTRA, WeatherField.TEMPERATURE, 11, 10, 11),
                        d(WeatherSource.SHORT, WeatherField.TEMPERATURE, 11, 12, 99)));
        assertThat(result.hourly().get("11:00").temperatureC()).isEqualTo(10);
    }

    @Test
    void currentBoundaryAndForecastExpiry() {
        var result = composer.composeTodayWeather(region, now.toLocalDate(), now,
                List.of(d(WeatherSource.OBSERVATION, WeatherField.TEMPERATURE, 10, 10, 3),
                        d(WeatherSource.ULTRA, WeatherField.TEMPERATURE, 13, 6, 5),
                        d(WeatherSource.SHORT, WeatherField.TEMPERATURE, 14, 0, 6)));
        assertThat(result.current().temperatureC()).isEqualTo(3);
        assertThat(result.hourly().get("13:00").temperatureC()).isEqualTo(5);
        var expired = composer.composeTodayWeather(region, now.toLocalDate(), now.plusSeconds(1),
                List.of(d(WeatherSource.OBSERVATION, WeatherField.TEMPERATURE, 10, 10, 3),
                        d(WeatherSource.ULTRA, WeatherField.TEMPERATURE, 13, 6, 5),
                        d(WeatherSource.SHORT, WeatherField.TEMPERATURE, 14, 0, 6)));
        assertThat(expired.current().temperatureC()).isNull();
        assertThat(expired.hourly().get("13:00")).isNull();
        assertThat(expired.hourly().get("14:00")).isNull();
        assertThat(expired.hourly().get("10:00").temperatureC()).isEqualTo(3);
    }

    @Test
    void pmIsNotCopiedIntoFutureAndDoesNotMixStations() {
        PollutantReading pm = new PollutantReading(0.0, "good", now.minusHours(1), now);
        var result = composer.composeTodayWeather(region, now.toLocalDate(), now,
                List.of(new WeatherDatum(WeatherSource.AIR, "중구", WeatherField.PM10, now, now, pm),
                        new WeatherDatum(WeatherSource.AIR, "다른곳", WeatherField.PM25, now, now, pm)));
        assertThat(result.current().airQuality().pm10().concentration()).isZero();
        assertThat(result.current().airQuality().pm25()).isNull();
        assertThat(result.hourly().get("13:00")).isNull();
    }

    @Test
    void dailyDoesNotUseHourlyExtrema() {
        var result = composer.composeTodayWeather(region, now.toLocalDate(), now,
                List.of(d(WeatherSource.OBSERVATION, WeatherField.TEMPERATURE, 11, 11, 20)));
        assertThat(result.daily().temperatureMaxC()).isNull();
    }

    @Test
    void precipitationPreservesBoundsAndRejectsQualitative() {
        var p = (PrecipitationRange) WeatherNormalizer.parsePrecipitation(WeatherSource.SHORT, "1mm 미만",
                now.minusHours(1), now);
        assertThat(p.lowerBound()).isZero();
        assertThat(p.upperBound()).isEqualTo(1);
        assertThat(p.lowerInclusive()).isTrue();
        assertThat(p.upperInclusive()).isFalse();
        assertThat(((PrecipitationRange) WeatherNormalizer.parsePrecipitation(WeatherSource.SHORT, "30.0~50.0mm",
                now.minusHours(1),
                now)).upperInclusive()).isFalse();
        assertThat(((ExactPrecipitation) WeatherNormalizer.parsePrecipitation(WeatherSource.SHORT, "강수없음",
                now.minusHours(1),
                now)).value()).isZero();
        assertThat(WeatherNormalizer.parsePrecipitation(WeatherSource.SHORT, "약한 비", now.minusHours(1), now)).isNull();
        assertThat(WeatherNormalizer.parsePrecipitation(WeatherSource.SHORT, "-999", now.minusHours(1), now)).isNull();
    }

    @Test
    void airMidnightAndFlagsUseOneHourGrade() {
        var tree = JsonMapper.builder().build().readTree("""
                [{"dataTime":"2026-11-14 24:00","pm10Value":"0","pm10Grade1h":"1","pm10Grade":"4","pm10Flag":null,
                  "pm25Value":"22","pm25Grade1h":"2","pm25Flag":"점검및교정"}]
                """);
        var data = WeatherNormalizer.normalizeAirQualityItems("중구", tree);
        assertThat(data).hasSize(1);
        assertThat(data.getFirst().target()).isEqualTo(now.withHour(0));
        assertThat(((PollutantReading) data.getFirst().value()).grade()).isEqualTo("good");
    }

    @Test
    void releaseBoundariesAndMidnight() {
        assertThat(WeatherSchedule.resolveLatestReleaseTime(WeatherSource.OBSERVATION, now.withMinute(11))).isEqualTo(
                now.minusHours(1));
        assertThat(WeatherSchedule.resolveLatestReleaseTime(WeatherSource.OBSERVATION, now.withMinute(12))).isEqualTo(
                now);
        assertThat(WeatherSchedule.resolveLatestReleaseTime(WeatherSource.ULTRA, now.withMinute(46))).isEqualTo(
                now.minusHours(1).plusMinutes(30));
        assertThat(WeatherSchedule.resolveLatestReleaseTime(WeatherSource.ULTRA, now.withMinute(47))).isEqualTo(
                now.plusMinutes(30));
        assertThat(WeatherSchedule.resolveLatestReleaseTime(WeatherSource.SHORT, now.withHour(0))).isEqualTo(
                now.minusDays(1).withHour(23));
    }

    @Test
    void afternoonRequestRetainsForecastValidAtTheHistoricalHour() {
        OffsetDateTime afternoon = now.withHour(18);
        OffsetDateTime midnight = now.withHour(0);
        OffsetDateTime previousRelease = midnight.minusHours(1);
        var data = List.of(
                new WeatherDatum(WeatherSource.SHORT, "60,127", WeatherField.SKY, midnight, previousRelease, "cloudy"),
                new WeatherDatum(WeatherSource.SHORT, "60,127", WeatherField.PROBABILITY, midnight,
                        previousRelease, 60.0),
                new WeatherDatum(WeatherSource.SHORT, "60,127", WeatherField.SKY, midnight.plusHours(1),
                        previousRelease.minusHours(13), "clear"),
                new WeatherDatum(WeatherSource.SHORT, "60,127", WeatherField.SKY, midnight.plusHours(2),
                        midnight.plusHours(3), "clear"));
        var result = composer.composeTodayWeather(region, afternoon.toLocalDate(), afternoon, data);
        assertThat(result.hourly().get("00:00").sky()).isEqualTo("cloudy");
        assertThat(result.hourly().get("00:00").precipitationProbabilityPercent()).isEqualTo(60);
        assertThat(result.hourly().get("01:00")).isNull();
        assertThat(result.hourly().get("02:00")).isNull();
    }

    @Test
    void dailyForecastSurvivesUntilDayEndAndUsesLatestAvailableIssue() {
        OffsetDateTime late = now.withHour(23);
        OffsetDateTime previousRelease = now.withHour(0).minusHours(1);
        var data = List.of(
                new WeatherDatum(WeatherSource.SHORT, "60,127", WeatherField.MIN, now.withHour(6),
                        previousRelease, 4.0),
                new WeatherDatum(WeatherSource.SHORT, "60,127", WeatherField.MAX, now.withHour(15),
                        previousRelease, 17.0),
                new WeatherDatum(WeatherSource.SHORT, "60,127", WeatherField.MAX, now.withHour(15),
                        now.withHour(11), 18.0),
                new WeatherDatum(WeatherSource.SHORT, "60,127", WeatherField.MIN, now.plusDays(1).withHour(6),
                        now.withHour(20), -1.0));
        var result = composer.composeTodayWeather(region, late.toLocalDate(), late, data);
        assertThat(result.daily().temperatureMinC()).isEqualTo(4);
        assertThat(result.daily().temperatureMaxC()).isEqualTo(18);
        var nextDay = composer.composeTodayWeather(region, late.plusDays(1).toLocalDate(), late.plusDays(1), data);
        assertThat(nextDay.daily().temperatureMinC()).isNull();
        assertThat(nextDay.daily().temperatureMaxC()).isNull();
    }
}
