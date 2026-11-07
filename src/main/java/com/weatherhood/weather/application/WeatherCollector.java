package com.weatherhood.weather.application;

import com.weatherhood.weather.domain.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WeatherCollector {
    private record Key(Grid grid, WeatherSource source, ZonedDateTime publication) {}
    private final WeatherGateway gateway;
    private final WeatherRepository repository;
    private final ExecutorService executor;
    private final Clock clock;
    private final Semaphore network;
    private final Semaphore capacity = new Semaphore(128);
    // shortcut: 수집 합치기는 단일 인스턴스 범위다. 다중 인스턴스 운영 시 저장소 잠금으로 확장한다.
    private final ConcurrentMap<Key, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentMap<Key, Instant> failures = new ConcurrentHashMap<>();
    private final Duration requestBudget;
    private final Duration cooldown;
    private final Duration activeWindow;

    public WeatherCollector(WeatherGateway gateway, WeatherRepository repository, ExecutorService executor, Clock clock,
            @Value("${weather.collection.concurrency:2}") int concurrency,
            @Value("${weather.collection.request-budget:PT20S}") Duration requestBudget,
            @Value("${weather.collection.failure-cooldown:PT1M}") Duration cooldown,
            @Value("${weather.collection.active-window:PT1H}") Duration activeWindow) {
        this.gateway = gateway; this.repository = repository; this.executor = executor; this.clock = clock;
        this.network = new Semaphore(concurrency); this.requestBudget = requestBudget;
        this.cooldown = cooldown; this.activeWindow = activeWindow;
    }

    public void refresh(Grid grid) {
        List<CompletableFuture<Void>> jobs = collect(grid, ZonedDateTime.now(clock));
        try {
            CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new))
                    .get(requestBudget.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException failure) {
            // Completed batches remain usable; incomplete requests never replace them.
        }
    }

    private List<CompletableFuture<Void>> collect(Grid grid, ZonedDateTime now) {
        if (!gateway.configured()) return List.of();
        long deadline = System.nanoTime() + requestBudget.toNanos();
        List<Key> keys = new ArrayList<>();
        keys.add(new Key(grid, WeatherSource.OBSERVATION, WeatherSource.OBSERVATION.latestPublication(now)));
        keys.add(new Key(grid, WeatherSource.ULTRA_SHORT_TERM_FORECAST,
                WeatherSource.ULTRA_SHORT_TERM_FORECAST.latestPublication(now)));
        keys.add(new Key(grid, WeatherSource.SHORT_TERM_FORECAST,
                WeatherSource.SHORT_TERM_FORECAST.latestPublication(now)));
        keys.add(new Key(grid, WeatherSource.SHORT_TERM_FORECAST, now.toLocalDate().minusDays(1).atTime(23, 0).atZone(now.getZone())));
        ZonedDateTime last = WeatherSource.OBSERVATION.latestPublication(now);
        for (ZonedDateTime time = now.toLocalDate().atStartOfDay(now.getZone()); !time.isAfter(last); time = time.plusHours(1))
            keys.add(new Key(grid, WeatherSource.OBSERVATION, time));
        return keys.stream().distinct().map(key -> submit(key, deadline)).toList();
    }

    private CompletableFuture<Void> submit(Key key, long deadline) {
        if (repository.find(key.grid(), key.source(), key.publication()).isPresent())
            return CompletableFuture.completedFuture(null);
        Instant failed = failures.get(key);
        if (failed != null && clock.instant().isBefore(failed.plus(cooldown)))
            return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> promise = new CompletableFuture<>();
        CompletableFuture<Void> existing = inFlight.putIfAbsent(key, promise);
        if (existing != null) return existing;
        if (!capacity.tryAcquire()) {
            inFlight.remove(key, promise);
            promise.complete(null);
            return promise;
        }
        executor.execute(() -> {
            boolean acquired = false;
            try {
                long remaining = deadline - System.nanoTime();
                acquired = remaining > 0 && network.tryAcquire(remaining, TimeUnit.NANOSECONDS);
                if (!acquired) return;
                if (repository.find(key.grid(), key.source(), key.publication()).isEmpty())
                    repository.save(gateway.fetch(key.grid(), key.source(), key.publication()));
                failures.remove(key);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException failure) {
                failures.put(key, clock.instant());
            } finally {
                if (acquired) network.release();
                capacity.release();
                inFlight.remove(key, promise);
                promise.complete(null);
            }
        });
        return promise;
    }

    @Scheduled(fixedDelayString = "${weather.collection.refresh-delay-ms:60000}")
    public void refreshActive() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        for (Grid grid : repository.activeGrids(now.minus(activeWindow))) collect(grid, now);
        repository.prune(now.minusHours(48));
        failures.entrySet().removeIf(entry -> entry.getValue().isBefore(clock.instant().minus(cooldown)));
    }
}
