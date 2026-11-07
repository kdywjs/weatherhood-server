package com.weatherhood.weather.infrastructure;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WeatherConfiguration {
    @Bean Clock weatherClock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
    @Bean HttpClient kmaHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Bean(destroyMethod = "close") ExecutorService weatherExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
