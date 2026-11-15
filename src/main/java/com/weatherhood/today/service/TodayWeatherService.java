package com.weatherhood.today.service;

import static com.weatherhood.today.service.WeatherSchedule.KST;

import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import com.weatherhood.today.dto.TodayWeatherData;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.entity.WeatherRegion;
import com.weatherhood.today.repository.WeatherRegionRepository;
import com.weatherhood.today.repository.WeatherStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 지역 확인부터 수집·저장 자료 조회·오늘 날씨 조합까지 조율하는 서비스.
 *
 * <p>요청 진입 시 한국 날짜를 고정하며 일부 정상값이 있으면 부분 응답을 제공한다.
 * HTTP 상태나 응답 헤더는 이 서비스에서 결정하지 않는다.
 */
@Service
public class TodayWeatherService {
    private final WeatherRegionRepository regionRepository;
    private final WeatherStore weatherStore;
    private final WeatherCollector weatherCollector;
    private final Clock clock;
    private final WeatherComposer composer = new WeatherComposer();

    public TodayWeatherService(WeatherRegionRepository regionRepository, WeatherStore weatherStore,
                               WeatherCollector weatherCollector, Clock clock) {
        this.regionRepository = regionRepository;
        this.weatherStore = weatherStore;
        this.weatherCollector = weatherCollector;
        this.clock = clock;
    }

    /**
     * 요청 진입 시점의 한국 날짜에 해당하는 날씨를 조회한다.
     *
     * @param regionId 조회할 내부 지역 ID
     * @return 24시간 슬롯·현재 관측·하루 예보를 포함한 결과
     * @throws ApplicationException 지역이 없거나 지원 대상이 아닌 경우 또는 유효한 날씨값이 전혀 없는 경우
     */
    public TodayWeatherData getTodayWeather(String regionId) {
        OffsetDateTime now = OffsetDateTime.now(clock).atZoneSameInstant(KST).toOffsetDateTime();
        LocalDate date = now.toLocalDate();
        WeatherRegion region = findSupportedRegion(regionId);
        Set<String> locations = findProviderLocations(region);
        OffsetDateTime start = date.atStartOfDay(KST).toOffsetDateTime();
        List<WeatherDatum> weatherData = new ArrayList<>(
                weatherStore.findWeatherData(locations, start.minusHours(2), start.plusDays(1)));
        List<WeatherDatum> collectedData = weatherCollector.collectRequiredWeatherData(region, date, now, weatherData);
        weatherData.clear();
        // Read once more to include another request's completed shared collection.
        weatherData.addAll(weatherStore.findWeatherData(locations, start.minusHours(2), start.plusDays(1)));
        weatherData.addAll(collectedData);
        TodayWeatherData response = composer.composeTodayWeather(region, date, now, weatherData);
        if (WeatherComposer.hasNoWeatherData(response)) {
            throw new ApplicationException(ErrorCode.WEATHER_UNAVAILABLE);
        }
        return response;
    }

    private WeatherRegion findSupportedRegion(String regionId) {
        WeatherRegion region = regionRepository.findById(regionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.REGION_NOT_FOUND));
        if (!region.supported()) {
            throw new ApplicationException(ErrorCode.UNSUPPORTED_REGION);
        }
        return region;
    }

    private Set<String> findProviderLocations(WeatherRegion region) {
        Set<String> locations = new HashSet<>();
        if (region.grid() != null) {
            locations.add(region.grid());
        }
        if (region.pm10Station() != null) {
            locations.add(region.pm10Station());
        }
        if (region.pm25Station() != null) {
            locations.add(region.pm25Station());
        }
        if (region.sunLocation() != null) {
            locations.add(region.sunLocation());
        }
        return locations;
    }
}
