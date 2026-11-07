package com.weatherhood.weather.infrastructure;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import com.weatherhood.weather.domain.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import tools.jackson.databind.json.JsonMapper;

class KmaWeatherGatewayTest {
    private static final ZonedDateTime BASE = ZonedDateTime.of(2026, 11, 2, 14, 0, 0, 0, ZoneId.of("Asia/Seoul"));
    private HttpServer server;
    private JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach void setup() throws Exception {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(datasource);
        jdbc = new JdbcTemplate(datasource);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    }
    @AfterEach void close() { server.stop(0); }

    private KmaWeatherGateway gateway(int calls) {
        return new KmaWeatherGateway(HttpClient.newHttpClient(), new KmaPayloadParser(json),
                new KmaRequestBudget(jdbc, calls, 10000000), Clock.fixed(BASE.toInstant(), BASE.getZone()),
                "secret+key/value=", "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(2));
    }

    private String page(int page) {
        return json.writeValueAsString(Map.of("response", Map.of("header", Map.of("resultCode", "00"),
                "body", Map.of("totalCount", 2, "pageNo", page, "numOfRows", 1, "items", Map.of("item", List.of(Map.of(
                        "nx", 60, "ny", 127, "baseDate", "20261102", "baseTime", "1400",
                        "fcstDate", "20261102", "fcstTime", "1600", "category", page == 1 ? "TMP" : "SKY",
                        "fcstValue", page == 1 ? "12.3" : "4")))))));
    }

    @Test void WTH_015_all_pages_are_collected_and_parameters_encoded_once() {
        AtomicInteger requests = new AtomicInteger();
        List<String> queries = Collections.synchronizedList(new ArrayList<>());
        server.createContext("/getVilageFcst", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            int page = requests.incrementAndGet();
            byte[] data = page(page).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data); exchange.close();
        });
        server.start();
        var batch = gateway(10).fetch(new Grid(60, 127), WeatherSource.SHORT_TERM_FORECAST, BASE);
        assertThat(requests).hasValue(2);
        assertThat(queries).allMatch(q -> q.contains("base_date=20261102&") && q.contains("authKey=secret%2Bkey%2Fvalue%3D"));
        assertThat(batch.samples().getFirst().values().temperatureC()).isEqualTo(12.3);
        assertThat(batch.samples().getFirst().values().sky()).isEqualTo("cloudy");
    }

    @Test void WTH_015_later_page_failure_does_not_return_partial_batch_or_expose_key() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/getVilageFcst", exchange -> {
            int number = requests.incrementAndGet();
            byte[] data = (number == 1 ? page(1) : "secret+key/value=").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(number == 1 ? 200 : 500, data.length);
            exchange.getResponseBody().write(data); exchange.close();
        });
        server.start();
        assertThatThrownBy(() -> gateway(10).fetch(new Grid(60, 127), WeatherSource.SHORT_TERM_FORECAST, BASE))
                .isInstanceOf(WeatherException.class).hasMessageNotContaining("secret").hasNoCause();
    }

    @Test void WTH_016_quota_survives_budget_object_recreation_and_preserves_failed_reservations() {
        LocalDate day = BASE.toLocalDate();
        var first = new KmaRequestBudget(jdbc, 1, 10000000);
        first.reserve(day);
        assertThatThrownBy(() -> new KmaRequestBudget(jdbc, 1, 10000000).reserve(day)).isInstanceOf(WeatherException.class);
        assertThat(jdbc.queryForObject("SELECT calls FROM kma_usage WHERE usage_day=?", Integer.class, day)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reserved_bytes FROM kma_usage WHERE usage_day=?", Long.class, day))
                .isEqualTo(KmaRequestBudget.MAX_RESPONSE_BYTES);
    }

    @Test void WTH_019_unconfigured_gateway_never_contacts_upstream() {
        var gateway = new KmaWeatherGateway(HttpClient.newHttpClient(), new KmaPayloadParser(json),
                new KmaRequestBudget(jdbc, 10, 10000000), Clock.systemUTC(), "", "http://localhost:1", Duration.ofSeconds(1));
        assertThat(gateway.configured()).isFalse();
        assertThatThrownBy(() -> gateway.fetch(new Grid(60, 127), WeatherSource.OBSERVATION, BASE))
                .isInstanceOf(WeatherException.class).hasNoCause();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kma_usage", Integer.class)).isZero();
    }

    @Test void WTH_015_repeated_page_is_not_accepted_as_complete() {
        server.createContext("/getVilageFcst", exchange -> {
            byte[] data = page(1).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data); exchange.close();
        });
        server.start();
        assertThatThrownBy(() -> gateway(10).fetch(new Grid(60, 127), WeatherSource.SHORT_TERM_FORECAST, BASE))
                .isInstanceOf(WeatherException.class);
    }

    @Test void WTH_021_oversized_body_is_cancelled_and_not_parsed() {
        server.createContext("/getVilageFcst", exchange -> {
            byte[] data = new byte[KmaRequestBudget.MAX_RESPONSE_BYTES + 1];
            exchange.sendResponseHeaders(200, data.length);
            try { exchange.getResponseBody().write(data); } catch (java.io.IOException cancelled) { /* Expected client cancellation. */ }
            finally { exchange.close(); }
        });
        server.start();
        assertThatThrownBy(() -> gateway(10).fetch(new Grid(60, 127), WeatherSource.SHORT_TERM_FORECAST, BASE))
                .isInstanceOf(WeatherException.class).hasNoCause();
    }

    @Test void WTH_021_timeout_covers_body_after_headers() {
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/getVilageFcst", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            try { release.await(2, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        var gateway = new KmaWeatherGateway(HttpClient.newHttpClient(), new KmaPayloadParser(json),
                new KmaRequestBudget(jdbc, 10, 10000000), Clock.fixed(BASE.toInstant(), BASE.getZone()),
                "test-only-key", "http://localhost:" + server.getAddress().getPort(), Duration.ofMillis(200));
        long start = System.nanoTime();
        try {
            assertThatThrownBy(() -> gateway.fetch(new Grid(60, 127), WeatherSource.SHORT_TERM_FORECAST, BASE))
                    .isInstanceOf(WeatherException.class).hasNoCause();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        } finally { release.countDown(); }
    }

    @Test void WTH_016_concurrent_quota_reservations_are_atomic() throws Exception {
        var quota = new KmaRequestBudget(jdbc, 3, 10000000);
        AtomicInteger accepted = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 16; i++) tasks.add(executor.submit(() -> {
                try { quota.reserve(BASE.toLocalDate()); accepted.incrementAndGet(); }
                catch (WeatherException limited) { /* Remaining callers must be rejected. */ }
            }));
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
        }
        assertThat(accepted.get()).isEqualTo(3);
    }
}
