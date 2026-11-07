package com.weatherhood.weather.application;

import com.weatherhood.weather.domain.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WeatherQueryService {
    public record Result(Grid grid, LocalDate date, WeatherTimeline.View view) {}
    public record OverviewResult(Grid grid, WeatherOverview overview) {}
    private final WeatherRepository repository;
    private final SupportedLocations locations;
    private final WeatherCollector collector;
    private final Clock clock;
    private final Duration staleTolerance;
    private final WeatherTimeline timeline = new WeatherTimeline();

    public WeatherQueryService(WeatherRepository repository, SupportedLocations locations,
            WeatherCollector collector, Clock clock,
            @Value("${weather.collection.stale-tolerance:PT0S}") Duration staleTolerance) {
        this.repository = repository; this.locations = locations; this.collector = collector;
        this.clock = clock; this.staleTolerance = staleTolerance;
    }

    public Result timeline(double lat, double lon, LocalDate date) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        if (date.isBefore(now.toLocalDate()) || date.isAfter(now.toLocalDate().plusDays(4)))
            throw new WeatherException(WeatherException.Code.UNSUPPORTED_DATE, "현재 조회할 수 없는 날짜입니다.");
        Grid grid = resolve(lat, lon, now);
        var batches = repository.find(grid);
        var view = timeline.assemble(batches, date.atStartOfDay(now.getZone()),
                date.plusDays(1).atStartOfDay(now.getZone()), now, staleTolerance);
        if (view.points().isEmpty()) {
            boolean knownOutsideForecast = date.isAfter(now.toLocalDate()) && batches.stream()
                    .filter(b -> b.source() == WeatherSource.SHORT_TERM_FORECAST)
                    .filter(b -> b.publishedAt().equals(WeatherSource.SHORT_TERM_FORECAST.latestPublication(now)))
                    .anyMatch(b -> b.samples().stream().noneMatch(s -> s.time().toLocalDate().equals(date)));
            if (knownOutsideForecast) throw new WeatherException(WeatherException.Code.UNSUPPORTED_DATE,
                    "해당 날짜의 시간별 자료는 아직 제공되지 않습니다.");
            throw WeatherException.unavailable();
        }
        return new Result(grid, date, view);
    }

    public OverviewResult overview(double lat, double lon) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        Grid grid = resolve(lat, lon, now);
        var view = timeline.assemble(repository.find(grid), now.toLocalDate().atStartOfDay(now.getZone()).minusHours(1),
                now.toLocalDate().plusDays(5).atStartOfDay(now.getZone()), now, staleTolerance);
        if (view.points().isEmpty()) throw WeatherException.unavailable();
        return new OverviewResult(grid, WeatherOverview.from(view, now));
    }

    private Grid resolve(double lat, double lon, ZonedDateTime now) {
        Grid grid = Grid.from(lat, lon);
        locations.requireSupported(grid);
        repository.touch(grid, now);
        collector.refresh(grid);
        return grid;
    }
}
