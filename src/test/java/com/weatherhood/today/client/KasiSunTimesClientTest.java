package com.weatherhood.today.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.weatherhood.today.dto.SunEvent;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.exception.WeatherProviderException.Reason;
import com.weatherhood.today.exception.WeatherProviderException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KasiSunTimesClientTest {
    private static final WeatherProvider.Request REQUEST = new WeatherProvider.Request(WeatherSource.SUN,
            "37.5841367,126.9706519", OffsetDateTime.parse("2026-11-15T00:00:00+09:00"));

    @Test
    void parsesProviderTimesAndRetainsReferenceLocation() throws Exception {
        var query = new AtomicReference<String>();
        var result = fetch(200, response("20261115", "0711  ", "172230"), query);
        assertThat(result.complete()).isTrue();
        assertThat(result.values()).hasSize(2);
        var sunrise = (SunEvent) result.values().getFirst().value();
        var sunset = (SunEvent) result.values().getLast().value();
        assertThat(sunrise.time()).isEqualTo("2026-11-15T07:11:00+09:00");
        assertThat(sunset.time()).isEqualTo("2026-11-15T17:22:30+09:00");
        assertThat(sunrise.referenceLocation()).isEqualTo("서울");
        assertThat(sunrise.referenceLatitude()).isEqualTo(37.566666);
        assertThat(result.values().getFirst().location()).isEqualTo(REQUEST.location());
        assertThat(query.get()).contains("locdate=20261115", "latitude=37.5841367", "longitude=126.9706519",
                "dnYn=Y", "serviceKey=test%2Bkey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-", "null", "2460", "071199", "711"})
    void missingOrInvalidSunrisePreservesSunset(String sunrise) throws Exception {
        var result = fetch(200, response("20261115", sunrise, "1722"), new AtomicReference<>());
        assertThat(result.complete()).isFalse();
        assertThat(result.values()).hasSize(1);
        assertThat(result.values().getFirst().field().name()).isEqualTo("SUNSET");
    }

    @Test
    void rejectsWrongDateAndReversedTimes() {
        assertInvalidResponse(response("20261114", "0711", "1722"));
        assertInvalidResponse(response("20261115", "1722", "0711"));
    }

    @Test
    void rejectsMalformedXmlAndExternalEntities() {
        assertInvalidResponse("not-xml");
        assertInvalidResponse("<!DOCTYPE response [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]>"
                + "<response>&secret;</response>");
    }

    @Test
    void authenticationErrorsDoNotExposeKeyOrResponseBody() {
        assertThatThrownBy(() -> fetch(401, "private-body", new AtomicReference<>()))
                .isInstanceOfSatisfying(WeatherProviderException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(Reason.AUTHENTICATION);
                    assertThat(exception.getMessage()).doesNotContain("test+key", "private-body");
                    assertThat(exception.getCause()).isNull();
                });
        assertThatThrownBy(() -> new KasiSunTimesClient("", "").fetchSunTimes(REQUEST))
                .isInstanceOfSatisfying(WeatherProviderException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.AUTHENTICATION));
    }

    private void assertInvalidResponse(String body) {
        assertThatThrownBy(() -> fetch(200, body, new AtomicReference<>()))
                .isInstanceOfSatisfying(WeatherProviderException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.INVALID_RESPONSE));
    }

    private String response(String date, String sunrise, String sunset) {
        return """
                <response><header><resultCode>00</resultCode></header><body><items><item>
                <locdate>%s</locdate><location>서울</location><latitudeNum>37.5666660</latitudeNum>
                <longitudeNum>126.9833330</longitudeNum><sunrise>%s</sunrise><sunset>%s</sunset>
                </item></items></body></response>
                """.formatted(date, sunrise, sunset);
    }

    private WeatherProvider.Result fetch(int status, String body, AtomicReference<String> query) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            return new KasiSunTimesClient("test+key", "http://127.0.0.1:" + server.getAddress().getPort() + "/")
                    .fetchSunTimes(REQUEST);
        } finally {
            server.stop(0);
        }
    }
}
