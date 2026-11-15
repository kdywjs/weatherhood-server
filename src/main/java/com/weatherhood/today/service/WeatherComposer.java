package com.weatherhood.today.service;

import static com.weatherhood.today.service.WeatherSchedule.KST;

import com.weatherhood.today.dto.AirQuality;
import com.weatherhood.today.dto.CurrentWeather;
import com.weatherhood.today.dto.DailyWeather;
import com.weatherhood.today.dto.HourlyWeather;
import com.weatherhood.today.dto.PollutantReading;
import com.weatherhood.today.dto.Precipitation;
import com.weatherhood.today.dto.RegionResponse;
import com.weatherhood.today.dto.SunEvent;
import com.weatherhood.today.dto.TodayWeatherData;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.entity.WeatherRegion;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 저장·수집한 자료에서 대상 시각에 유효한 값을 선택하는 순수 조합 로직.
 *
 * <p>관측과 예보의 출처·발표 시각을 구분하고, 미세먼지를 미래 슬롯에 복사하지 않는다.
 * 모든 값이 없는 시간도 null 슬롯으로 유지한다. DB와 외부 HTTP를 호출하지 않는다.
 */
public class WeatherComposer {
    public TodayWeatherData composeTodayWeather(WeatherRegion region, LocalDate date, OffsetDateTime now,
                                                List<WeatherDatum> weatherData) {
        List<WeatherDatum> data = weatherData.stream().filter(datum -> matchesRegion(region, datum)).toList();
        Map<String, HourlyWeather> hours = new LinkedHashMap<>();
        for (int hourIndex = 0; hourIndex < 24; hourIndex++) {
            OffsetDateTime targetTime = date.atTime(hourIndex, 0).atZone(KST).toOffsetDateTime();
            HourlyWeather hour = composeHourlyWeather(data, targetTime, now);
            hours.put(String.format(Locale.ROOT, "%02d:00", hourIndex), hour.isEmpty() ? null : hour);
        }
        CurrentWeather current = new CurrentWeather(
                (Double) findLatestObservedValue(data, WeatherField.TEMPERATURE, now),
                AirQuality.of((PollutantReading) findLatestObservedValue(data, WeatherField.PM10, now),
                        (PollutantReading) findLatestObservedValue(data, WeatherField.PM25, now)));
        DailyWeather daily = composeDailyWeather(data, date, now);
        return new TodayWeatherData(new RegionResponse(region.id(), region.name()), date, "Asia/Seoul", current, daily,
                hours);
    }

    private boolean matchesRegion(WeatherRegion region, WeatherDatum datum) {
        return switch (datum.field()) {
            case SUNRISE, SUNSET -> Objects.equals(region.sunLocation(), datum.location());
            case PM10 -> Objects.equals(region.pm10Station(), datum.location());
            case PM25 -> Objects.equals(region.pm25Station(), datum.location());
            default -> Objects.equals(region.grid(), datum.location());
        };
    }

    /**
     * 같은 지역의 자료에서 오늘 최저·최고 예보를 선택한다. 전날 23시 이후 발표 중 최신 유효값을 오늘 하루 유지하며 시간별 기온으로 대체하지 않는다.
     *
     * @param data 지역 필터링을 마친 자료
     * @param date 제공 날짜
     * @param now  요청 시각
     * @return 하루 기온과 일출·일몰; 확보하지 못한 항목은 각각 null
     */
    public DailyWeather composeDailyWeather(List<WeatherDatum> data, LocalDate date, OffsetDateTime now) {
        return new DailyWeather(findDailyForecastTemperature(data, WeatherField.MIN, date, now),
                findDailyForecastTemperature(data, WeatherField.MAX, date, now),
                findSunEventTime(data, WeatherField.SUNRISE, date, now),
                findSunEventTime(data, WeatherField.SUNSET, date, now));
    }

    public boolean isUsable(WeatherDatum datum, OffsetDateTime now) {
        if (datum.value() == null || datum.issued().isAfter(now)) {
            return false;
        }
        return switch (datum.source()) {
            case SUN -> datum.target().toLocalDate().equals(now.toLocalDate());
            case OBSERVATION, AIR -> !datum.target().isAfter(now);
            case ULTRA, SHORT -> {
                // 과거 슬롯은 그 시각의 유효성을 판단하고, 현재·미래 슬롯은 요청 시각을 사용한다.
                OffsetDateTime referenceTime = datum.target().isBefore(now) ? datum.target() : now;
                yield !datum.issued().isAfter(datum.target()) && !datum.issued()
                        .isBefore(referenceTime.minusHours(datum.source() == WeatherSource.ULTRA ? 6 : 12));
            }
        };
    }

    private Object findPreferredWeatherValue(List<WeatherDatum> data, WeatherField field, OffsetDateTime targetTime,
                                             OffsetDateTime now) {
        return data.stream()
                .filter(datum -> datum.field() == field && datum.target().isEqual(targetTime) && isUsable(datum, now))
                .sorted(Comparator.comparingInt((WeatherDatum datum) -> datum.source().ordinal())
                        .thenComparing(WeatherDatum::issued, Comparator.reverseOrder()))
                .map(WeatherDatum::value).findFirst().orElse(null);
    }

    private Object findLatestObservedValue(List<WeatherDatum> data, WeatherField field, OffsetDateTime now) {
        return data.stream().filter(datum -> datum.field() == field && (datum.source() == WeatherSource.OBSERVATION
                        || datum.source() == WeatherSource.AIR)
                        && !datum.target().isAfter(now) && !datum.target().isBefore(now.minusHours(2)))
                .sorted(Comparator.comparing(WeatherDatum::target).reversed()).map(WeatherDatum::value).findFirst()
                .orElse(null);
    }

    private Double findDailyForecastTemperature(List<WeatherDatum> data, WeatherField field, LocalDate date,
                                                OffsetDateTime now) {
        return data.stream()
                .filter(datum -> datum.field() == field && datum.source() == WeatherSource.SHORT && datum.target()
                        .toLocalDate().equals(date)
                        && datum.value() != null && !datum.issued().isAfter(now)
                        && !datum.issued().isBefore(date.atStartOfDay(WeatherSchedule.KST).minusHours(1)
                        .toOffsetDateTime()))
                .sorted(Comparator.comparing(WeatherDatum::issued).reversed()).map(datum -> (Double) datum.value())
                .findFirst().orElse(null);
    }

    private OffsetDateTime findSunEventTime(List<WeatherDatum> data, WeatherField field, LocalDate date,
                                            OffsetDateTime now) {
        return data.stream()
                .filter(datum -> datum.source() == WeatherSource.SUN && datum.field() == field
                        && datum.target().toLocalDate().equals(date) && !datum.issued().isAfter(now)
                        && datum.value() instanceof SunEvent)
                .sorted(Comparator.comparing(WeatherDatum::issued).reversed())
                .map(datum -> ((SunEvent) datum.value()).time())
                .filter(Objects::nonNull)
                .map(time -> time.atZoneSameInstant(KST).toOffsetDateTime())
                .filter(time -> time.toLocalDate().equals(date))
                .findFirst().orElse(null);
    }

    public static boolean hasNoWeatherData(TodayWeatherData targetTime) {
        return targetTime.current().temperatureC() == null && targetTime.current().airQuality() == null
                && targetTime.daily().temperatureMinC() == null && targetTime.daily().temperatureMaxC() == null
                && targetTime.daily().sunriseAt() == null && targetTime.daily().sunsetAt() == null
                && targetTime.hourly().values().stream().allMatch(Objects::isNull);
    }

    private HourlyWeather composeHourlyWeather(List<WeatherDatum> data, OffsetDateTime targetTime, OffsetDateTime now) {
        return new HourlyWeather((Double) findPreferredWeatherValue(data, WeatherField.TEMPERATURE, targetTime, now),
                (String) findPreferredWeatherValue(data, WeatherField.SKY, targetTime, now),
                (String) findPreferredWeatherValue(data, WeatherField.PRECIPITATION_TYPE, targetTime, now),
                (Precipitation) findPreferredWeatherValue(data, WeatherField.PRECIPITATION_AMOUNT, targetTime, now),
                (Double) findPreferredWeatherValue(data, WeatherField.WIND, targetTime, now),
                (Double) findPreferredWeatherValue(data, WeatherField.HUMIDITY, targetTime, now),
                (Double) findPreferredWeatherValue(data, WeatherField.PROBABILITY, targetTime, now),
                AirQuality.of((PollutantReading) findPreferredWeatherValue(data, WeatherField.PM10, targetTime, now),
                        (PollutantReading) findPreferredWeatherValue(data, WeatherField.PM25, targetTime, now)));
    }
}
