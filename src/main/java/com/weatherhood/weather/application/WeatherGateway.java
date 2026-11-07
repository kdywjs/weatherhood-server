package com.weatherhood.weather.application;

import com.weatherhood.weather.domain.Grid;
import com.weatherhood.weather.domain.WeatherBatch;
import com.weatherhood.weather.domain.WeatherSource;
import java.time.ZonedDateTime;

public interface WeatherGateway {
    WeatherBatch fetch(Grid grid, WeatherSource source, ZonedDateTime publication);
    boolean configured();
}
