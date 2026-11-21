package com.weatherhood.today.service;

import static com.weatherhood.today.service.WeatherSchedule.KST;

import com.weatherhood.today.client.WeatherProvider.Request;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.entity.WeatherRegion;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 최신 발표와 오늘 누락 항목을 기준으로 중복 없는 수집 후보를 만든다.
 *
 * <p>호출·재시도 제한은 수집기가 적용한다. 다른 격자의 자료는 누락 판정에 사용하지 않는다.
 */
public class WeatherRequestPlanner {
    /**
     * 저장 자료를 기준으로 수집 요청을 선택하는 계획기를 생성한다.
     */
    public WeatherRequestPlanner() {
    }

    private static final Set<WeatherField> OBSERVATION_FIELDS = Set.of(WeatherField.TEMPERATURE,
            WeatherField.PRECIPITATION_TYPE, WeatherField.PRECIPITATION_AMOUNT, WeatherField.WIND,
            WeatherField.HUMIDITY);
    private static final Set<WeatherField> HOURLY_FIELDS = Set.of(WeatherField.TEMPERATURE, WeatherField.SKY,
            WeatherField.PRECIPITATION_TYPE, WeatherField.PRECIPITATION_AMOUNT, WeatherField.WIND,
            WeatherField.HUMIDITY, WeatherField.PROBABILITY);
    private final WeatherComposer composer = new WeatherComposer();

    /**
     * 최신 자료, 하루 공통 정보, 과거 예보 보충, 과거 관측 순으로 후보를 만든다.
     *
     * @param region   조회 지역
     * @param date     요청 시작 시 고정한 한국 날짜
     * @param now      한국 시각
     * @param existing 저장된 자료
     * @return 공급 종류·위치·발표별로 중복 제거된 수집 후보
     */
    public List<Request> planRequests(WeatherRegion region, LocalDate date, OffsetDateTime now,
                                      List<WeatherDatum> existing) {
        Set<Request> requests = new LinkedHashSet<>();
        if (region.grid() != null) {
            for (WeatherSource source : List.of(WeatherSource.OBSERVATION, WeatherSource.ULTRA, WeatherSource.SHORT)) {
                requests.add(new Request(source, region.grid(), WeatherSchedule.resolveLatestReleaseTime(source, now)));
            }
        }
        for (String station : findStations(region)) {
            requests.add(new Request(WeatherSource.AIR, station,
                    WeatherSchedule.resolveLatestReleaseTime(WeatherSource.AIR, now)));
        }
        if (region.sunLocation() != null) {
            var sunData = existing.stream().filter(datum -> region.sunLocation().equals(datum.location())).toList();
            var daily = composer.composeDailyWeather(sunData, date, now);
            if (daily.sunriseAt() == null || daily.sunsetAt() == null) {
                requests.add(new Request(WeatherSource.SUN, region.sunLocation(),
                        date.atStartOfDay(KST).toOffsetDateTime()));
            }
        }
        if (region.grid() != null) {
            List<WeatherDatum> gridData = existing.stream()
                    .filter(datum -> region.grid().equals(datum.location()) && datum.value() != null).toList();
            addDailyForecastRequests(requests, region, date, now, gridData);
            addHistoricalForecastRequests(requests, region, date, now, gridData);
            addMissingObservationRequests(requests, region, date, now, gridData);
        }
        return List.copyOf(requests);
    }

    private Set<String> findStations(WeatherRegion region) {
        Set<String> stations = new LinkedHashSet<>();
        if (region.pm10Station() != null) {
            stations.add(region.pm10Station());
        }
        if (region.pm25Station() != null) {
            stations.add(region.pm25Station());
        }
        return stations;
    }

    private void addDailyForecastRequests(Set<Request> requests, WeatherRegion region, LocalDate date,
                                          OffsetDateTime now, List<WeatherDatum> data) {
        var daily = composer.composeDailyWeather(data, date, now);
        if (daily.temperatureMinC() == null || daily.temperatureMaxC() == null) {
            requests.add(new Request(WeatherSource.SHORT, region.grid(),
                    date.atStartOfDay(KST).minusHours(1).toOffsetDateTime()));
        }
    }

    private void addHistoricalForecastRequests(Set<Request> requests, WeatherRegion region, LocalDate date,
                                               OffsetDateTime now, List<WeatherDatum> data) {
        OffsetDateTime midnight = date.atStartOfDay(KST).toOffsetDateTime();
        for (OffsetDateTime target = midnight; !target.isAfter(now); target = target.plusHours(1)) {
            OffsetDateTime targetTime = target;
            Set<WeatherField> available = data.stream()
                    .filter(datum -> datum.target().isEqual(targetTime) && composer.isUsable(datum, now))
                    .map(WeatherDatum::field).collect(Collectors.toSet());
            if (!available.containsAll(HOURLY_FIELDS)) {
                requests.add(new Request(WeatherSource.SHORT, region.grid(),
                        WeatherSchedule.resolveLatestReleaseTime(WeatherSource.SHORT, target)));
            }
        }
    }

    private void addMissingObservationRequests(Set<Request> requests, WeatherRegion region, LocalDate date,
                                               OffsetDateTime now, List<WeatherDatum> data) {
        OffsetDateTime latest = WeatherSchedule.resolveLatestReleaseTime(WeatherSource.OBSERVATION, now);
        for (OffsetDateTime target = date.atStartOfDay(KST).toOffsetDateTime(); target.isBefore(latest);
             target = target.plusHours(1)) {
            OffsetDateTime targetTime = target;
            Set<WeatherField> available = data.stream()
                    .filter(datum -> datum.source() == WeatherSource.OBSERVATION
                            && datum.target().isEqual(targetTime) && composer.isUsable(datum, now))
                    .map(WeatherDatum::field).collect(Collectors.toSet());
            if (!available.containsAll(OBSERVATION_FIELDS)) {
                requests.add(new Request(WeatherSource.OBSERVATION, region.grid(), target));
            }
        }
    }
}
