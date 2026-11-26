package com.weatherhood.weekly.service;

import static com.weatherhood.today.service.WeatherSchedule.KST;

import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import com.weatherhood.today.dto.RegionResponse;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.repository.WeatherRegionRepository;
import com.weatherhood.today.repository.WeatherStore;
import com.weatherhood.today.service.WeatherCollector;
import com.weatherhood.weekly.dto.WeeklyWeatherResponse;
import com.weatherhood.weekly.dto.WeeklyWeatherResponse.Day;
import com.weatherhood.weekly.dto.WeeklyWeatherResponse.Period;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 행정동의 격자별 단기예보를 날짜별 목록으로 조합한다. */
@Service
public class WeeklyWeatherService {
    private final WeatherRegionRepository regions;
    private final WeatherCollector collector;
    private final WeatherStore store;
    private final Clock clock;

    /**
     * 주간 목록을 조회하는 서비스를 구성한다.
     *
     * @param regions 행정동 저장소
     * @param collector 발표별 수집기
     * @param store 날씨 저장소
     * @param clock 조회 날짜 기준 시계
     */
    public WeeklyWeatherService(WeatherRegionRepository regions, WeatherCollector collector, WeatherStore store,
                                Clock clock) {
        this.regions = regions;
        this.collector = collector;
        this.store = store;
        this.clock = clock;
    }

    /**
     * 지역의 예보 대상일을 조회한다.
     *
     * @param regionId 행정동 ID
     * @return 한국 날짜 기준 오늘부터 일주일 안의 실제 예보
     */
    public WeeklyWeatherResponse findWeeklyWeather(String regionId) {
        var region = regions.findById(regionId).orElseThrow(() -> new ApplicationException(ErrorCode.REGION_NOT_FOUND));
        if (!region.supported() || region.grid() == null) {
            throw new ApplicationException(ErrorCode.UNSUPPORTED_REGION);
        }
        var now = OffsetDateTime.ofInstant(clock.instant(), KST);
        var start = now.toLocalDate().atStartOfDay(KST).toOffsetDateTime();
        collector.refreshWeeklyForecast(region.grid(), now);
        var data = store.findWeeklyForecast(region.grid(), start, start.plusDays(7));
        var days = data.stream()
                .collect(Collectors.groupingBy(value -> value.target().toLocalDate(), TreeMap::new, Collectors.toList()))
                .entrySet().stream().map(entry -> summarizeDay(entry.getKey(), entry.getValue())).toList();
        if (days.isEmpty()) {
            throw new ApplicationException(ErrorCode.WEEKLY_WEATHER_UNAVAILABLE);
        }
        return new WeeklyWeatherResponse(new RegionResponse(region.id(), region.name()), KST.getId(), days);
    }

    private Day summarizeDay(LocalDate date, List<WeatherDatum> data) {
        return new Day(date, (Double) latestValue(data, WeatherField.MIN), (Double) latestValue(data, WeatherField.MAX),
                selectPeriod(data, 9), selectPeriod(data, 15));
    }

    private Period selectPeriod(List<WeatherDatum> data, int referenceHour) {
        var times = data.stream().filter(d -> d.field() == WeatherField.SKY
                        || d.field() == WeatherField.PRECIPITATION_TYPE)
                .map(WeatherDatum::target).filter(time -> time.getHour() / 12 == referenceHour / 12);
        return times.min(Comparator.comparingInt((OffsetDateTime time) -> Math.abs(time.getHour() - referenceHour))
                        .thenComparing(Comparator.naturalOrder()))
                .map(time -> {
                    var slot = data.stream().filter(d -> d.target().equals(time)).toList();
                    return new Period(time, (String) latestValue(slot, WeatherField.SKY),
                            (String) latestValue(slot, WeatherField.PRECIPITATION_TYPE));
                }).orElse(null);
    }

    private Object latestValue(List<WeatherDatum> data, WeatherField field) {
        return data.stream().filter(d -> d.field() == field)
                .max(Comparator.comparing(WeatherDatum::issued).thenComparing(WeatherDatum::target))
                .map(WeatherDatum::value).orElse(null);
    }
}
