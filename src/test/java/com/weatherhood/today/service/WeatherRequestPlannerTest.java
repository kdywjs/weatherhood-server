package com.weatherhood.today.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.weatherhood.today.client.WeatherProvider.Request;
import com.weatherhood.today.dto.ExactPrecipitation;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.entity.WeatherRegion;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeatherRequestPlannerTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-15T18:30:00+09:00");
    private final WeatherRegion region = new WeatherRegion("test", "테스트 지역", true, 60, 127, "중구", "중구");
    private final WeatherRequestPlanner planner = new WeatherRequestPlanner();

    @Test
    void coldAfternoonPlansPastForecastReleasesAndDailyFallbackWithoutDuplicates() {
        var requests = planner.planRequests(region, now.toLocalDate(), now, List.of());
        var releases = requests.stream().filter(request -> request.source() == WeatherSource.SHORT)
                .map(Request::release).toList();
        assertThat(releases).contains(now.withHour(0).withMinute(0).minusHours(1),
                now.withHour(2).withMinute(0), now.withHour(5).withMinute(0), now.withHour(11).withMinute(0));
        assertThat(requests).doesNotHaveDuplicates();
        assertThat(requests.stream().filter(request -> request.source() == WeatherSource.AIR)).hasSize(1);
        assertThat(requests).allSatisfy(request -> assertThat(request.release()).isBeforeOrEqualTo(now));
    }

    @Test
    void fourObservationFieldsDoNotHideMissingHumidityOrOtherLocations() {
        OffsetDateTime target = now.withHour(0).withMinute(0);
        List<WeatherDatum> data = new ArrayList<>();
        for (WeatherField field : List.of(WeatherField.TEMPERATURE, WeatherField.PRECIPITATION_TYPE,
                WeatherField.PRECIPITATION_AMOUNT, WeatherField.WIND)) {
            Object value = switch (field) {
                case PRECIPITATION_TYPE -> "none";
                case PRECIPITATION_AMOUNT -> new ExactPrecipitation(0, target.minusHours(1), target);
                default -> 0.0;
            };
            data.add(new WeatherDatum(WeatherSource.OBSERVATION, region.grid(), field, target, target, value));
        }
        data.add(new WeatherDatum(WeatherSource.OBSERVATION, "61,128", WeatherField.HUMIDITY, target, target, 50.0));
        Request missingHour = new Request(WeatherSource.OBSERVATION, region.grid(), target);
        assertThat(planner.planRequests(region, now.toLocalDate(), now, data)).contains(missingHour);
        data.add(new WeatherDatum(WeatherSource.OBSERVATION, region.grid(), WeatherField.HUMIDITY, target, target,
                50.0));
        assertThat(planner.planRequests(region, now.toLocalDate(), now, data)).doesNotContain(missingHour);
    }

    @Test
    void completedDayDoesNotScheduleHistoricalCallsAgain() {
        List<WeatherDatum> data = new ArrayList<>();
        for (int hour = 0; hour <= 18; hour++) {
            OffsetDateTime target = now.withHour(hour).withMinute(0);
            for (WeatherField field : List.of(WeatherField.TEMPERATURE, WeatherField.HUMIDITY, WeatherField.WIND)) {
                data.add(new WeatherDatum(WeatherSource.OBSERVATION, region.grid(), field, target, target, 0.0));
            }
            data.add(new WeatherDatum(WeatherSource.OBSERVATION, region.grid(), WeatherField.PRECIPITATION_TYPE,
                    target, target, "none"));
            data.add(new WeatherDatum(WeatherSource.OBSERVATION, region.grid(), WeatherField.PRECIPITATION_AMOUNT,
                    target, target, new ExactPrecipitation(0, target.minusHours(1), target)));
            OffsetDateTime issue = WeatherSchedule.resolveLatestReleaseTime(WeatherSource.SHORT, target);
            data.add(new WeatherDatum(WeatherSource.SHORT, region.grid(), WeatherField.SKY, target, issue, "clear"));
            data.add(
                    new WeatherDatum(WeatherSource.SHORT, region.grid(), WeatherField.PROBABILITY, target, issue, 0.0));
        }
        OffsetDateTime previous = now.withHour(0).withMinute(0).minusHours(1);
        data.add(new WeatherDatum(WeatherSource.SHORT, region.grid(), WeatherField.MIN,
                now.withHour(6).withMinute(0), previous, 5.0));
        data.add(new WeatherDatum(WeatherSource.SHORT, region.grid(), WeatherField.MAX,
                now.withHour(15).withMinute(0), previous, 18.0));
        assertThat(planner.planRequests(region, now.toLocalDate(), now, data)).hasSize(4)
                .allSatisfy(request -> assertThat(request.release())
                        .isEqualTo(WeatherSchedule.resolveLatestReleaseTime(request.source(), now)));
    }

    @Test
    void midnightDoesNotDuplicatePreviousDayReleaseOrScheduleFutureObservations() {
        OffsetDateTime midnight = now.withHour(0).withMinute(0);
        var requests = planner.planRequests(region, midnight.toLocalDate(), midnight, List.of());
        assertThat(requests).doesNotHaveDuplicates();
        assertThat(requests.stream().filter(request -> request.source() == WeatherSource.SHORT)).hasSize(1);
        assertThat(requests).allSatisfy(request -> assertThat(request.release()).isBeforeOrEqualTo(midnight));
    }
}
