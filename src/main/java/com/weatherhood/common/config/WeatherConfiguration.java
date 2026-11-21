package com.weatherhood.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * 날씨 조회에서 사용할 공통 시계 Bean 설정.
 *
 * <p>서버 기본 시간대에 의존하지 않도록 UTC 시계를 제공하고, 서비스에서 한국 시각으로 변환한다.
 */
@Configuration
public class WeatherConfiguration {
    /**
     * Spring이 날씨 조회용 공통 시계를 등록할 때 사용하는 생성자.
     */
    public WeatherConfiguration() {
    }

    @Bean
    Clock weatherClock() {
        return Clock.systemUTC();
    }
}
