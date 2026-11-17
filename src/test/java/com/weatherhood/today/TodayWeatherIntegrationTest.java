package com.weatherhood.today;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import com.weatherhood.TestcontainersConfiguration;
import com.weatherhood.common.response.ApiResponse;
import com.weatherhood.today.client.WeatherNormalizer;
import com.weatherhood.today.client.WeatherProvider;
import com.weatherhood.today.dto.ExactPrecipitation;
import com.weatherhood.today.dto.PrecipitationRange;
import com.weatherhood.today.dto.SunEvent;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.entity.WeatherRegion;
import com.weatherhood.today.repository.WeatherRegionRepository;
import com.weatherhood.today.repository.WeatherStore;
import com.weatherhood.today.service.WeatherRequestPlanner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TodayWeatherIntegrationTest {
    @Autowired
    WeatherStore store;
    @Autowired
    WeatherRegionRepository regions;
    @Autowired
    JsonMapper json;
    @MockitoBean
    WeatherProvider provider;
    @MockitoBean
    Clock clock;
    @LocalServerPort
    int port;
    OffsetDateTime now = OffsetDateTime.parse("2026-11-15T12:30:00+09:00");

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(provider.fetchWeatherData(any())).thenReturn(new WeatherProvider.Result(List.of(), false));
    }

    @AfterEach
    void removeTestRegions() {
        regions.deleteAllById(
                List.of("http-partial", "http-historical", "unsupported", "unmapped", "http-sun", "http-sun-partial"));
    }

    HttpResponse<String> get(String query) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/weather/today" + query))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void nationwideSeedHasOpaqueIdsAndGridMapping() {
        var rows = regions.findAll();
        assertThat(rows).hasSize(3565);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.id()).matches("[0-9a-f-]{36}");
            assertThat(r.grid()).isNotNull();
            assertThat(r.pm10Station()).isNotBlank();
        });
        assertThat(rows.stream().map(WeatherRegion::grid).distinct().count()).isLessThan(rows.size());
    }

    @Test
    void upsertIsAtomicByIssueAndMissingNeverOverwrites() {
        OffsetDateTime target = now.withHour(18).withMinute(0);
        store.saveWeatherData(
                List.of(new WeatherDatum(WeatherSource.ULTRA, "test-grid", WeatherField.TEMPERATURE, target, now,
                        15.0)), now);
        store.saveWeatherData(
                List.of(new WeatherDatum(WeatherSource.ULTRA, "test-grid", WeatherField.TEMPERATURE, target,
                        now.minusHours(1), 30.0)), now.plusSeconds(1));
        store.saveWeatherData(
                List.of(new WeatherDatum(WeatherSource.ULTRA, "test-grid", WeatherField.TEMPERATURE, target,
                        now.plusHours(1), null)), now.plusSeconds(2));
        var rows = store.findWeatherData(Set.of("test-grid"), target, target.plusHours(1));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().value()).isEqualTo(15.0);
        store.saveWeatherData(
                List.of(new WeatherDatum(WeatherSource.OBSERVATION, "test-grid", WeatherField.TEMPERATURE, target,
                        target, 12.0)), target);
        assertThat(store.findWeatherData(Set.of("test-grid"), target, target.plusHours(1))).hasSize(2);
    }

    @Test
    void apiReturnsTwentyFourKeysNullsAndNoInternalMetadata() throws Exception {
        regions.save(new WeatherRegion("http-partial", "테스트 지역", true, 61, 128, null, null));
        OffsetDateTime t = now.withMinute(0);
        store.saveWeatherData(
                List.of(new WeatherDatum(WeatherSource.OBSERVATION, "61,128", WeatherField.TEMPERATURE, t, t, 0.0)),
                now);
        var response = get("?regionId=http-partial");
        assertThat(response.statusCode()).isEqualTo(200);
        var envelope = json.readTree(response.body());
        assertThat(envelope.size()).isEqualTo(2);
        assertThat(envelope.path("error").isNull()).isTrue();
        var root = envelope.path("data");
        assertThat(root.path("hourly").size()).isEqualTo(24);
        assertThat(root.path("hourly").path("00:00").isNull()).isTrue();
        assertThat(root.path("current").path("temperatureC").asDouble()).isZero();
        assertThat(response.body()).doesNotContain("generatedAt", "status", "issues", "hasStaleData", "authKey",
                "serviceKey");
        assertThat(root.size()).isEqualTo(6);
    }

    @Test
    void errorsDistinguishInputUnknownUnsupportedAndUnavailable() throws Exception {
        assertThat(get("").statusCode()).isEqualTo(400);
        assertThat(get("?regionId=").statusCode()).isEqualTo(400);
        assertThat(get("?regionId=a&regionId=b").statusCode()).isEqualTo(400);
        assertThat(get("?regionId=missing").statusCode()).isEqualTo(404);
        regions.save(new WeatherRegion("unsupported", "테스트", false, null, null, null, null));
        assertThat(get("?regionId=unsupported").statusCode()).isEqualTo(422);
        regions.save(new WeatherRegion("unmapped", "테스트", true, null, null, null, null));
        var response = get("?regionId=unmapped");
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("WEATHER_UNAVAILABLE", "\"retryAfterSeconds\":null");
    }

    @Test
    void collectionStateAndDailyQuotaSurviveServiceInstances() {
        store.recordCollectionAttempt("test-state", now, now.plusMinutes(5), false);
        assertThat(store.findNextAttemptTime("test-state").toInstant()).isEqualTo(now.plusMinutes(5).toInstant());
        assertThat(store.tryReserveProviderCall("test", now.toLocalDate(), 2)).isTrue();
        assertThat(store.tryReserveProviderCall("test", now.toLocalDate(), 2)).isTrue();
        assertThat(store.tryReserveProviderCall("test", now.toLocalDate(), 2)).isFalse();
    }

    @Test
    void separatedPrecipitationDtosPreserveExactAndRangeContracts() {
        var exact = json.readTree(json.writeValueAsString(
                ApiResponse.success(new ExactPrecipitation(0, now.minusHours(1), now))));
        assertThat(exact.path("error").isNull()).isTrue();
        assertThat(exact.path("data").size()).isEqualTo(5);
        assertThat(exact.path("data").path("kind").asString()).isEqualTo("exact");
        assertThat(exact.path("data").path("value").asDouble()).isZero();
        assertThat(exact.path("data").path("periodEnd").asString()).isEqualTo("2026-11-15T12:30:00+09:00");

        var range = json.readTree(json.writeValueAsString(
                ApiResponse.success(new PrecipitationRange(0, 1.0, true, false, now.minusHours(1), now))));
        assertThat(range.path("data").size()).isEqualTo(8);
        assertThat(range.path("data").path("kind").asString()).isEqualTo("range");
        assertThat(range.path("data").path("lowerInclusive").asBoolean()).isTrue();
        assertThat(range.path("data").path("upperInclusive").asBoolean()).isFalse();
        assertThat(range.path("data").has("value")).isFalse();
    }

    @Test
    void afternoonColdRequestPersistsHistoricalSkyAndDailyForecastFromProviderItems() throws Exception {
        OffsetDateTime afternoon = now.withHour(18);
        when(clock.instant()).thenReturn(afternoon.toInstant());
        regions.save(new WeatherRegion("http-historical", "과거 보충 테스트", true, 62, 129, null, null));
        OffsetDateTime previousRelease = afternoon.withHour(0).withMinute(0).minusHours(1);
        var items = json.readTree("""
                [{"baseDate":"20261114","baseTime":"2300","fcstDate":"20261115","fcstTime":"0000",
                  "category":"SKY","fcstValue":"4"},
                 {"baseDate":"20261114","baseTime":"2300","fcstDate":"20261115","fcstTime":"0000",
                  "category":"POP","fcstValue":"60"},
                 {"baseDate":"20261114","baseTime":"2300","fcstDate":"20261115","fcstTime":"0600",
                  "category":"TMN","fcstValue":"5"},
                 {"baseDate":"20261114","baseTime":"2300","fcstDate":"20261115","fcstTime":"1500",
                  "category":"TMX","fcstValue":"18"}]
                """);
        when(provider.fetchWeatherData(any())).thenAnswer(invocation -> {
            WeatherProvider.Request request = invocation.getArgument(0);
            if (request.source() == WeatherSource.SHORT && request.location().equals("62,129")
                    && request.release().isEqual(previousRelease)) {
                return new WeatherProvider.Result(WeatherNormalizer.normalizeKmaItems(request.source(),
                        request.location(), items), true);
            }
            return new WeatherProvider.Result(List.of(), false);
        });
        for (int attempt = 0; attempt < 2; attempt++) {
            var response = get("?regionId=http-historical");
            assertThat(response.statusCode()).isEqualTo(200);
            var data = json.readTree(response.body()).path("data");
            assertThat(data.path("hourly").path("00:00").path("sky").asString()).isEqualTo("cloudy");
            assertThat(data.path("hourly").path("00:00").has("empty")).isFalse();
            assertThat(data.path("daily").path("temperatureMinC").asDouble()).isEqualTo(5);
            assertThat(data.path("daily").path("temperatureMaxC").asDouble()).isEqualTo(18);
        }
        assertThat(store.findWeatherData(Set.of("62,129"), afternoon.withHour(0).withMinute(0),
                afternoon.withHour(0).withMinute(0).plusDays(1))).hasSize(4);
    }

    @Test
    void sunTimesAreReusedAllDayAndRequestedAgainForNextDate() throws Exception {
        var region = regions.save(new WeatherRegion("http-sun", "일출 테스트", true, null, null, null, null,
                37.5841367, 126.9706519));
        var calls = new AtomicInteger();
        when(provider.fetchWeatherData(any())).thenAnswer(invocation -> {
            WeatherProvider.Request request = invocation.getArgument(0);
            assertThat(request.source()).isEqualTo(WeatherSource.SUN);
            calls.incrementAndGet();
            return new WeatherProvider.Result(sunData(region.sunLocation(), request.release()), true);
        });
        var first = get("?regionId=http-sun");
        assertThat(first.statusCode()).isEqualTo(200);
        var daily = json.readTree(first.body()).path("data").path("daily");
        assertThat(daily.path("sunriseAt").asString()).isEqualTo("2026-11-15T07:11:00+09:00");
        assertThat(daily.path("sunsetAt").asString()).isEqualTo("2026-11-15T17:22:00+09:00");
        assertThat(first.body()).doesNotContain("referenceLocation", "referenceLatitude", "referenceLongitude");
        when(clock.instant()).thenReturn(now.withHour(23).withMinute(59).toInstant());
        assertThat(get("?regionId=http-sun").statusCode()).isEqualTo(200);
        assertThat(calls.get()).isEqualTo(1);
        OffsetDateTime midnight = now.withHour(0).withMinute(0);
        var saved = store.findWeatherData(Set.of(region.sunLocation()), midnight, midnight.plusDays(1));
        assertThat(saved).hasSize(2);
        assertThat(((SunEvent) saved.getFirst().value()).referenceLocation()).isEqualTo("서울");
        // 저장값만으로 완료 판정하므로 프로세스가 재시작되어도 추가 호출이 필요 없다.
        assertThat(new WeatherRequestPlanner().planRequests(region, now.toLocalDate(), now, saved)).isEmpty();
        when(clock.instant()).thenReturn(midnight.plusDays(1).toInstant());
        var next = get("?regionId=http-sun");
        assertThat(json.readTree(next.body()).path("data").path("daily").path("sunriseAt").asString())
                .isEqualTo("2026-11-16T07:11:00+09:00");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void partialSunTimesRetryAfterFiveMinutesAndThenStopForTheDay() throws Exception {
        var region = regions.save(new WeatherRegion("http-sun-partial", "일출 부분 테스트", true,
                null, null, null, null, 35.1, 129.1));
        var calls = new AtomicInteger();
        when(provider.fetchWeatherData(any())).thenAnswer(invocation -> {
            WeatherProvider.Request request = invocation.getArgument(0);
            var data = sunData(region.sunLocation(), request.release());
            return calls.incrementAndGet() == 1
                    ? new WeatherProvider.Result(List.of(data.getFirst()), false)
                    : new WeatherProvider.Result(List.of(data.getLast()), false);
        });
        var first = json.readTree(get("?regionId=http-sun-partial").body()).path("data").path("daily");
        assertThat(first.path("sunriseAt").isNull()).isFalse();
        assertThat(first.path("sunsetAt").isNull()).isTrue();
        when(clock.instant()).thenReturn(now.plusMinutes(4).toInstant());
        assertThat(get("?regionId=http-sun-partial").statusCode()).isEqualTo(200);
        assertThat(calls.get()).isEqualTo(1);
        when(clock.instant()).thenReturn(now.plusMinutes(5).toInstant());
        var completed = json.readTree(get("?regionId=http-sun-partial").body()).path("data").path("daily");
        assertThat(completed.path("sunriseAt").isNull()).isFalse();
        assertThat(completed.path("sunsetAt").isNull()).isFalse();
        when(clock.instant()).thenReturn(now.plusHours(3).toInstant());
        get("?regionId=http-sun-partial");
        assertThat(calls.get()).isEqualTo(2);
    }

    private List<WeatherDatum> sunData(String location, OffsetDateTime midnight) {
        return List.of(new WeatherDatum(WeatherSource.SUN, location, WeatherField.SUNRISE, midnight, midnight,
                        new SunEvent(midnight.withHour(7).withMinute(11), "서울", 37.566666, 126.983333)),
                new WeatherDatum(WeatherSource.SUN, location, WeatherField.SUNSET, midnight, midnight,
                        new SunEvent(midnight.withHour(17).withMinute(22), "서울", 37.566666, 126.983333)));
    }

}
