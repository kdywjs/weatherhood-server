package com.weatherhood.weather.domain;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface WeatherRepository {
    List<WeatherBatch> find(Grid grid);
    Optional<WeatherBatch> find(Grid grid, WeatherSource source, ZonedDateTime publication);
    void save(WeatherBatch batch);
    void touch(Grid grid, ZonedDateTime now);
    List<Grid> activeGrids(ZonedDateTime since);
    void prune(ZonedDateTime before);
}
