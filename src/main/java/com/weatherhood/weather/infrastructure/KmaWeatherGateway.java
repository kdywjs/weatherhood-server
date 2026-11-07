package com.weatherhood.weather.infrastructure;

import com.weatherhood.weather.application.WeatherGateway;
import com.weatherhood.weather.domain.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KmaWeatherGateway implements WeatherGateway {
    private final HttpClient http;
    private final KmaPayloadParser parser;
    private final KmaRequestBudget budget;
    private final Clock clock;
    private final String key;
    private final String baseUrl;
    private final Duration timeout;

    public KmaWeatherGateway(HttpClient http, KmaPayloadParser parser, KmaRequestBudget budget, Clock clock,
            @Value("${weather.kma.auth-key:}") String key,
            @Value("${weather.kma.base-url}") String baseUrl,
            @Value("${weather.kma.timeout:PT3S}") Duration timeout) {
        this.http = http; this.parser = parser; this.budget = budget; this.clock = clock;
        this.key = key; this.baseUrl = baseUrl; this.timeout = timeout;
    }

    @Override public boolean configured() { return !key.isBlank(); }

    @Override public WeatherBatch fetch(Grid grid, WeatherSource source, ZonedDateTime publication) {
        if (!configured()) throw WeatherException.unavailable();
        List<Map<?, ?>> items = new ArrayList<>();
        int expected = -1;
        for (int page = 1; page <= 20; page++) {
            var result = parser.page(request(grid, source, publication, page));
            if (result.pageNumber() != page) throw WeatherException.unavailable();
            if (expected != -1 && expected != result.totalCount()) throw WeatherException.unavailable();
            expected = result.totalCount();
            if (result.items().isEmpty()) throw WeatherException.unavailable();
            items.addAll(result.items());
            if (items.size() == expected) return parser.batch(grid, source, publication,
                    ZonedDateTime.now(clock), items);
            if (items.size() > expected) throw WeatherException.unavailable();
        }
        throw WeatherException.unavailable();
    }

    private String request(Grid grid, WeatherSource source, ZonedDateTime publication, int page) {
        LocalDate day = LocalDate.now(clock);
        budget.reserve(day);
        CompletableFuture<HttpResponse<byte[]>> responseFuture = null;
        try {
            String query = "?dataType=JSON&numOfRows=1000&pageNo=" + page
                    + "&base_date=" + publication.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&base_time=" + publication.format(DateTimeFormatter.ofPattern("HHmm"))
                    + "&nx=" + grid.x() + "&ny=" + grid.y()
                    + "&authKey=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + source.operation() + query))
                    .timeout(timeout).GET().build();
            responseFuture = http.sendAsync(request, info -> new LimitedBodySubscriber());
            HttpResponse<byte[]> response = responseFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            byte[] bytes = response.body();
            budget.complete(day, bytes.length);
            if (response.statusCode() != 200) throw WeatherException.unavailable();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (InterruptedException error) {
            if (responseFuture != null) responseFuture.cancel(true);
            Thread.currentThread().interrupt();
            throw WeatherException.unavailable();
        } catch (Exception error) {
            if (responseFuture != null) responseFuture.cancel(true);
            // Never attach HTTP exceptions: their messages can contain the authKey query.
            throw WeatherException.unavailable();
        }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > KmaRequestBudget.MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(WeatherException.unavailable());
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable ignored) { body.completeExceptionally(WeatherException.unavailable()); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
