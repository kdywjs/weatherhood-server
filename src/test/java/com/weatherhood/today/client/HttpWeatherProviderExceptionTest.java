package com.weatherhood.today.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.exception.WeatherProviderException.Reason;
import com.weatherhood.today.exception.WeatherProviderException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpWeatherProviderExceptionTest {
    private static final WeatherProvider.Request REQUEST = new WeatherProvider.Request(
            WeatherSource.AIR, "테스트측정소", OffsetDateTime.parse("2026-11-15T12:00:00+09:00"));

    @Test
    void missingKeyProducesAuthenticationFailure() {
        var provider = new HttpWeatherProvider(JsonMapper.builder().build(), "", "", "", "",
                new KasiSunTimesClient("", ""));

        assertThatThrownBy(() -> provider.fetchWeatherData(REQUEST))
                .isInstanceOfSatisfying(WeatherProviderException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(Reason.AUTHENTICATION);
                    assertThat(exception.isProviderWide()).isTrue();
                });
    }

    @Test
    void malformedJsonIsAProviderResponseFailure() throws Exception {
        assertProviderFailure(200, "invalid-json", Reason.INVALID_RESPONSE);
    }

    @Test
    void unauthorizedResponseDoesNotExposeItsBodyOrKey() throws Exception {
        assertProviderFailure(401, "private-provider-body", Reason.AUTHENTICATION);
    }

    private void assertProviderFailure(int status, String body, Reason expectedReason) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var response = exchange.getResponseBody()) {
                response.write(bytes);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            var provider = new HttpWeatherProvider(JsonMapper.builder().build(), "test-key", "test-key", url, url,
                    new KasiSunTimesClient("test-key", url));

            assertThatThrownBy(() -> provider.fetchWeatherData(REQUEST))
                    .isInstanceOfSatisfying(WeatherProviderException.class, exception -> {
                        assertThat(exception.reason()).isEqualTo(expectedReason);
                        assertThat(exception.getMessage()).doesNotContain(body, "test-key", url);
                        assertThat(exception.getCause()).isNull();
                    });
        } finally {
            server.stop(0);
        }
    }
}
