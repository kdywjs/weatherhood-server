package com.weatherhood.weather.infrastructure;

import com.weatherhood.weather.domain.Grid;
import com.weatherhood.weather.domain.SupportedLocations;
import com.weatherhood.weather.domain.WeatherException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class KmaSupportedLocations implements SupportedLocations {
    private final Set<Grid> grids;

    public KmaSupportedLocations() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("kma-supported-grids.csv").getInputStream(), StandardCharsets.UTF_8))) {
            grids = reader.lines().skip(1).map(line -> line.split(","))
                    .map(parts -> new Grid(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])))
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (grids.isEmpty()) throw new IllegalStateException("기상청 지원 격자 목록이 비어 있습니다.");
    }

    @Override public void requireSupported(Grid grid) {
        if (!grids.contains(grid)) throw new WeatherException(WeatherException.Code.UNSUPPORTED_LOCATION,
                "현재 지원하는 국내 지역 격자에 포함되지 않는 위치입니다.");
    }
}
