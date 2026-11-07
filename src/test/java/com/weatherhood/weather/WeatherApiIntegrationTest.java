package com.weatherhood.weather;

import static org.assertj.core.api.Assertions.*;
import com.weatherhood.weather.application.*;
import com.weatherhood.weather.domain.*;
import com.weatherhood.weather.infrastructure.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:weather-api-test;DB_CLOSE_DELAY=-1",
        "weather.collection.refresh-delay-ms=3600000"
})
@Import(WeatherApiIntegrationTest.Fixtures.class)
class WeatherApiIntegrationTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 2, 15, 12, 0, 0, KST);
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired FixtureGateway gateway;
    @Autowired WeatherRepository repository;
    @Autowired WeatherCollector collector;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();

    @TestConfiguration
    static class Fixtures {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW.toInstant(), KST); }
        @Bean @Primary FixtureGateway fixtureGateway() { return new FixtureGateway(); }
    }

    static class FixtureGateway implements WeatherGateway {
        final ConcurrentMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        volatile boolean enabled = true;
        volatile boolean fail = false;
        @Override public boolean configured() { return enabled; }
        @Override public WeatherBatch fetch(Grid grid, WeatherSource source, ZonedDateTime publication) {
            counts.computeIfAbsent(source + "/" + publication, ignored -> new AtomicInteger()).incrementAndGet();
            if (fail) throw WeatherException.unavailable();
            List<WeatherBatch.Sample> samples = new ArrayList<>();
            if (source == WeatherSource.OBSERVATION) {
                samples.add(sample(publication, source));
            } else {
                ZonedDateTime start = publication.plusHours(1).withMinute(0);
                int hours = source == WeatherSource.ULTRA_SHORT_TERM_FORECAST ? 6 : 72;
                for (int i = 0; i < hours; i++) samples.add(sample(start.plusHours(i), source));
            }
            return new WeatherBatch(grid, source, publication, NOW, samples);
        }
        private WeatherBatch.Sample sample(ZonedDateTime time, WeatherSource source) {
            boolean observed = source == WeatherSource.OBSERVATION;
            var rain = new WeatherValues.Precipitation("exact", 0.0, null, null, null, null, null,
                    "mm", time.minusHours(1), time);
            var values = new WeatherValues(observed ? 12.4 : 15.0, observed ? null : "cloudy", "none",
                    observed ? null : 10.0, 2.0, 60.0, rain,
                    !observed && time.getHour() == 6 ? 8.0 : null,
                    !observed && time.getHour() == 15 ? 18.0 : null, null);
            return new WeatherBatch.Sample(time, 60, values);
        }
    }

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM weather_batch"); jdbc.update("DELETE FROM weather_location"); jdbc.update("DELETE FROM kma_usage");
        gateway.counts.clear(); gateway.enabled = true; gateway.fail = false;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private Map<?, ?> body(HttpResponse<String> response) { return json.readValue(response.body(), Map.class); }

    @Test void WTH_017_018_cold_start_backfills_midnight_and_second_request_uses_shared_storage() throws Exception {
        var first = get("/v1/weather/timeline?lat=37.5665&lon=126.978&date=2026-11-02");
        assertThat(first.statusCode()).isEqualTo(200);
        var response = body(first);
        assertThat(response.get("provider")).isEqualTo("kma");
        assertThat(response.get("timezone")).isEqualTo("Asia/Seoul");
        var points = (List<?>) response.get("points");
        assertThat(points).hasSize(24);
        assertThat(((Map<?, ?>) points.getFirst()).get("time")).isEqualTo("2026-11-02T00:00:00+09:00");
        assertThat(((Map<?, ?>) points.getFirst()).get("temperatureC")).isEqualTo(12.4);
        assertThat(((Map<?, ?>) points.getFirst()).get("source")).isEqualTo("mixed");
        int calls = gateway.counts.values().stream().mapToInt(AtomicInteger::get).sum();
        assertThat(get("/v1/weather/timeline?lat=37.5666&lon=126.978&date=2026-11-02").statusCode()).isEqualTo(200);
        assertThat(gateway.counts.values().stream().mapToInt(AtomicInteger::get).sum()).isEqualTo(calls);
        assertThat(get("/v1/weather?lat=37.5665&lon=126.978").statusCode()).isEqualTo(200);
        assertThat(get("/v1/weather/timeline?lat=37.5665&lon=126.978&date=2026-11-06").statusCode()).isEqualTo(422);
    }

    @Test void WTH_002_017_invalid_and_unsupported_inputs_are_separate() throws Exception {
        for (String path : List.of("/v1/weather?lat=NaN&lon=126", "/v1/weather?lat=91&lon=126",
                "/v1/weather?lat=37.5", "/v1/weather/timeline?lat=37.5&lon=127&date=2026-02-30"))
            assertThat(get(path).statusCode()).as(path).isEqualTo(400);
        assertThat(get("/v1/weather?lat=35.6762&lon=139.6503").statusCode()).isEqualTo(422);
        assertThat(get("/v1/weather/timeline?lat=37.5665&lon=126.978&date=2026-11-01").statusCode()).isEqualTo(422);
        assertThat(gateway.counts).isEmpty();
    }

    @Test void WTH_017_019_no_available_data_is_503_without_secrets() throws Exception {
        gateway.enabled = false;
        var result = get("/v1/weather?lat=37.5665&lon=126.978");
        assertThat(result.statusCode()).isEqualTo(503);
        assertThat(result.headers().firstValue("Retry-After")).hasValue("60");
        assertThat(result.body()).contains("WEATHER_UNAVAILABLE", "retryAfterSeconds").doesNotContain("authKey", "apihub.kma");
    }

    @Test void WTH_012_concurrent_requests_collect_each_key_once() throws Exception {
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < 12; i++) jobs.add(threads.submit(() -> collector.refresh(new Grid(60, 127))));
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
        }
        assertThat(gateway.counts).isNotEmpty();
        assertThat(gateway.counts.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
    }

    @Test void WTH_013_failure_keeps_existing_data_and_cooldown_avoids_repeat_calls() {
        var grid = new Grid(61, 127);
        var preserved = gateway.fetch(grid, WeatherSource.OBSERVATION, NOW.withHour(1).withMinute(0));
        repository.save(preserved);
        gateway.counts.clear(); gateway.fail = true;
        collector.refresh(grid);
        int calls = gateway.counts.values().stream().mapToInt(AtomicInteger::get).sum();
        collector.refresh(grid);
        assertThat(repository.find(grid)).containsExactly(preserved);
        assertThat(gateway.counts.values().stream().mapToInt(AtomicInteger::get).sum()).isEqualTo(calls);
    }

    @Test void WTH_014_file_database_reopens_and_retention_keeps_future_forecasts(@TempDir Path directory) {
        String url = "jdbc:h2:file:" + directory.resolve("weather").toString().replace('\\', '/') + ";DB_CLOSE_ON_EXIT=FALSE";
        var datasource = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(datasource);
        var first = new JdbcWeatherRepository(new JdbcTemplate(datasource), json);
        var grid = new Grid(60, 127);
        var oldTime = NOW.minusDays(3);
        var future = new WeatherBatch(grid, WeatherSource.SHORT_TERM_FORECAST, oldTime, oldTime,
                List.of(new WeatherBatch.Sample(NOW.plusDays(1), 60, new WeatherValues(15.0, "clear", "none", null, null, null, null, null, null, null))));
        first.save(future);
        var second = new JdbcWeatherRepository(new JdbcTemplate(new DriverManagerDataSource(url, "sa", "")), json);
        assertThat(second.find(grid)).containsExactly(future);
        second.prune(NOW.minusHours(48));
        assertThat(second.find(grid)).containsExactly(future);
    }
}
