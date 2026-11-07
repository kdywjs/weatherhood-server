package com.weatherhood.weather.presentation;

import com.weatherhood.weather.application.WeatherQueryService;
import java.time.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/weather")
public class WeatherController {
    private final WeatherQueryService service;
    public WeatherController(WeatherQueryService service) { this.service = service; }

    @GetMapping
    public WeatherResponse.Overview overview(@RequestParam double lat, @RequestParam double lon) {
        return WeatherResponse.overview(service.overview(lat, lon));
    }

    @GetMapping("/timeline")
    public WeatherResponse.Timeline timeline(@RequestParam double lat, @RequestParam double lon, @RequestParam LocalDate date) {
        return WeatherResponse.timeline(service.timeline(lat, lon, date));
    }
}
