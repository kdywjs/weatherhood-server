package com.weatherhood.today.service;

import static com.weatherhood.today.service.WeatherSchedule.KST;

import com.weatherhood.today.client.WeatherProvider.Request;
import com.weatherhood.today.client.WeatherProvider.Result;
import com.weatherhood.today.client.WeatherProvider;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.entity.WeatherRegion;
import com.weatherhood.today.exception.WeatherCollectionException;
import com.weatherhood.today.exception.WeatherProviderException;
import com.weatherhood.today.repository.WeatherStore;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 요청이 발생했을 때 필요한 공급 자료를 수집하고 저장하는 서비스.
 *
 * <p>동일 자료의 진행 중 요청을 이 애플리케이션 인스턴스 안에서 공유한다.
 * 전체 수집 대기 예산은 5초이며 호출당 2초를 확보할 수 없으면 새로운 호출을 시작하지 않는다. 공급처 실패는 부분 수집으로 처리하지만 내부 저장 실패는 업무 오류와 구분한다.
 */
@Service
public class WeatherCollector {
    private final WeatherRequestPlanner requestPlanner = new WeatherRequestPlanner();
    private final WeatherStore weatherStore;
    private final WeatherProvider provider;
    private final int kmaDailyLimit;
    private final int airDailyLimit;
    private final int kasiDailyLimit;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, CompletableFuture<List<WeatherDatum>>> inFlightRequests =
            new ConcurrentHashMap<>();
    private final Map<String, Semaphore> providerPermits = Map.of("kma", new Semaphore(4, true), "air",
            new Semaphore(4, true), "kasi", new Semaphore(4, true));
    private final ConcurrentMap<String, Instant> providerBlockedUntil = new ConcurrentHashMap<>();

    /**
     * 의존 객체와 외부 설정을 받아 WeatherCollector를 구성한다.
     *
     * @param weatherStore   날씨 및 수집 상태 저장소
     * @param provider       외부 날씨 공급처 클라이언트
     * @param kmaDailyLimit  기상청 일일 호출 상한
     * @param airDailyLimit  에어코리아 일일 호출 상한
     * @param kasiDailyLimit 한국천문연구원 일일 호출 상한
     */
    public WeatherCollector(WeatherStore weatherStore, WeatherProvider provider,
                            @Value("${weather.kma-daily-limit:1000}") int kmaDailyLimit,
                            @Value("${weather.air-daily-limit:500}") int airDailyLimit,
                            @Value("${weather.kasi-daily-limit:10000}") int kasiDailyLimit) {
        if (kmaDailyLimit < 1 || airDailyLimit < 1 || kasiDailyLimit < 1) {
            throw new IllegalArgumentException("Provider daily limits must be positive");
        }
        this.weatherStore = weatherStore;
        this.provider = provider;
        this.kmaDailyLimit = kmaDailyLimit;
        this.airDailyLimit = airDailyLimit;
        this.kasiDailyLimit = kasiDailyLimit;
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    /**
     * 갱신 대상 자료를 수집하여 저장하고 이번 호출에서 확보한 값을 반환한다.
     *
     * @param region   조회 지역과 공급 위치 매핑
     * @param date     요청 진입 시 고정한 한국 날짜
     * @param now      갱신 여부 판단에 사용할 한국 시각
     * @param existing 이미 저장한 자료; 과거 관측의 누락 판단에 사용
     * @return 이번 수집에서 확보한 값, 실패하거나 대기 상한에 도달하면 부분 목록 또는 빈 목록
     * @throws WeatherCollectionException 비동기 수집 작업에서 내부 실패가 발생한 경우
     */
    public List<WeatherDatum> collectRequiredWeatherData(WeatherRegion region, LocalDate date, OffsetDateTime now,
                                                         List<WeatherDatum> existing) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Queue<Request> jobs = new ConcurrentLinkedQueue<>(requestPlanner.planRequests(region, date, now, existing));
        List<WeatherDatum> acquired = new CopyOnWriteArrayList<>();
        List<Future<?>> workers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            workers.add(executor.submit(() -> {
                Request job;
                while (!Thread.currentThread().isInterrupted()
                        && deadline - System.nanoTime() >= TimeUnit.SECONDS.toNanos(2) && (job = jobs.poll()) != null) {
                    acquired.addAll(fetchRequiredWeatherData(job, now, deadline));
                }
            }));
        }
        awaitWorkers(workers, deadline);
        workers.forEach(worker -> worker.cancel(true));
        return List.copyOf(acquired);
    }

    private List<WeatherDatum> fetchRequiredWeatherData(Request request, OffsetDateTime now, long deadline) {
        Instant barrier = providerBlockedUntil.get(request.provider());
        if (barrier != null && barrier.isAfter(now.toInstant())) {
            return List.of();
        }
        OffsetDateTime next = weatherStore.findNextAttemptTime(request.key());
        if (next != null && next.isAfter(now)) {
            return List.of();
        }
        CompletableFuture<List<WeatherDatum>> currentFlight = new CompletableFuture<>();
        var existingFlight = inFlightRequests.putIfAbsent(request.key(), currentFlight);
        if (existingFlight != null) {
            return awaitExistingRequest(existingFlight, deadline);
        }
        Semaphore semaphore = providerPermits.get(request.provider());
        boolean permit = false;
        try {
            long wait = deadline - System.nanoTime() - TimeUnit.SECONDS.toNanos(2);
            if (wait < 0) {
                return List.of();
            }
            permit = semaphore.tryAcquire(wait, TimeUnit.NANOSECONDS);
            if (!permit || Thread.currentThread().isInterrupted()) {
                return List.of();
            }
            barrier = providerBlockedUntil.get(request.provider());
            if (barrier != null && barrier.isAfter(now.toInstant())) {
                return List.of();
            }
            next = weatherStore.findNextAttemptTime(request.key());
            if (next != null && next.isAfter(now)) {
                return List.of();
            }
            if (!weatherStore.tryReserveProviderCall(request.provider(), now.toLocalDate(),
                    switch (request.source()) {
                        case AIR -> airDailyLimit;
                        case SUN -> kasiDailyLimit;
                        default -> kmaDailyLimit;
                    })) {
                providerBlockedUntil.put(request.provider(),
                        now.toLocalDate().plusDays(1).atStartOfDay(KST).toInstant());
                return List.of();
            }
            return fetchAndStore(request, now, currentFlight);
        } catch (WeatherProviderException exception) {
            OffsetDateTime retry = now.plusSeconds(Math.max(300, exception.retryAfterSeconds()));
            if (exception.isProviderWide()) {
                providerBlockedUntil.put(request.provider(),
                        exception.isAuthenticationFailure() ? Instant.MAX : retry.toInstant());
            }
            weatherStore.recordCollectionAttempt(request.key(), now, retry, false);
            currentFlight.complete(List.of());
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (RuntimeException exception) {
            currentFlight.completeExceptionally(exception);
            throw exception;
        } finally {
            if (permit) {
                semaphore.release();
            }
            currentFlight.complete(List.of());
            inFlightRequests.remove(request.key(), currentFlight);
        }
    }

    private void awaitWorkers(List<Future<?>> workers, long deadline) {
        for (Future<?> worker : workers) {
            try {
                long left = deadline - System.nanoTime();
                if (left > 0) {
                    worker.get(left, TimeUnit.NANOSECONDS);
                }
            } catch (TimeoutException exception) {
                break;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException exception) {
                throw new WeatherCollectionException(exception.getCause());
            }
        }
    }

    private OffsetDateTime calculateNextAttemptTime(Request request, OffsetDateTime now, Result result) {
        if (request.source() == WeatherSource.SUN && result.complete()) {
            return request.release().plusDays(1);
        }
        OffsetDateTime retry = now.plusMinutes(5);
        if (result.complete()) {
            boolean historical = request.release()
                    .isBefore(WeatherSchedule.resolveLatestReleaseTime(request.source(), now));
            // 과거 자료도 부분 결측이 남으면 다시 수집할 수 있어야 한다.
            // 완전한 과거 슬롯은 요청 계획에서 제외하므로 매 요청마다 재조회하지 않는다.
            retry = historical ? now.plusMinutes(5) : WeatherSchedule.calculateNextCheckTime(request.source(), now);
            if (!historical && (request.source() == WeatherSource.OBSERVATION
                    || request.source() == WeatherSource.ULTRA) && retry.isAfter(now.plusMinutes(10))) {
                retry = now.plusMinutes(10);
            }
        }
        return retry;
    }

    private List<WeatherDatum> awaitExistingRequest(CompletableFuture<List<WeatherDatum>> existingFlight,
                                                    long deadline) {
        try {
            return existingFlight.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (TimeoutException | CancellationException exception) {
            return List.of();
        } catch (ExecutionException exception) {
            throw new WeatherCollectionException(exception.getCause());
        }
    }

    private List<WeatherDatum> fetchAndStore(Request request, OffsetDateTime now,
                                             CompletableFuture<List<WeatherDatum>> currentFlight) {
        Result result = provider.fetchWeatherData(request);
        OffsetDateTime collected = OffsetDateTime.now(KST);
        weatherStore.saveWeatherData(result.values(), collected);
        OffsetDateTime retry = calculateNextAttemptTime(request, now, result);
        weatherStore.recordCollectionAttempt(request.key(), now, retry, result.complete());
        currentFlight.complete(result.values());
        return result.values();
    }
}
