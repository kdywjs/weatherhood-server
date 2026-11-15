package com.weatherhood.today.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weatherhood.today.client.WeatherProvider.Request;
import com.weatherhood.today.client.WeatherProvider.Result;
import com.weatherhood.today.client.WeatherProvider;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.entity.WeatherRegion;
import com.weatherhood.today.repository.WeatherStore;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WeatherCollectorTest {
    @Test
    void historicalResponseWithMissingFieldsCanBeRetriedAfterCooldown() {
        WeatherStore store = mock(WeatherStore.class);
        OffsetDateTime first = OffsetDateTime.parse("2026-11-15T12:30:00+09:00");
        OffsetDateTime midnight = first.withHour(0).withMinute(0);
        WeatherRegion region = new WeatherRegion("test", "테스트 지역", true, 60, 127, null, null);
        Request historical = new Request(WeatherSource.OBSERVATION, region.grid(), midnight);
        Map<String, OffsetDateTime> attempts = new ConcurrentHashMap<>();
        AtomicInteger historicalCalls = new AtomicInteger();
        when(store.findNextAttemptTime(anyString())).thenAnswer(invocation -> attempts.get(invocation.getArgument(0)));
        when(store.tryReserveProviderCall(anyString(), any(), anyInt())).thenReturn(true);
        doAnswer(invocation -> {
            attempts.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(store).recordCollectionAttempt(anyString(), any(), any(), anyBoolean());
        WeatherProvider provider = request -> {
            if (request.equals(historical)) {
                historicalCalls.incrementAndGet();
                // 전송은 완전하지만 필수 관측값 중 기온만 정상인 응답.
                return new Result(List.of(new WeatherDatum(WeatherSource.OBSERVATION, region.grid(),
                        WeatherField.TEMPERATURE, midnight, midnight, 10.0)), true);
            }
            return new Result(List.of(), false);
        };
        WeatherCollector collector = new WeatherCollector(store, provider, 1000, 500, 10000);
        try {
            var stored = collector.collectRequiredWeatherData(region, first.toLocalDate(), first, List.of());
            assertThat(historicalCalls.get()).isEqualTo(1);
            collector.collectRequiredWeatherData(region, first.toLocalDate(), first.plusMinutes(4), stored);
            assertThat(historicalCalls.get()).isEqualTo(1);
            collector.collectRequiredWeatherData(region, first.toLocalDate(), first.plusMinutes(5), stored);
            assertThat(historicalCalls.get()).isEqualTo(2);
        } finally {
            collector.close();
        }
    }
}
