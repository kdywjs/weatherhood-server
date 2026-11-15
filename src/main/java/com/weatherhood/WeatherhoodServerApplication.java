package com.weatherhood;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * Weatherhood 서버의 Spring Boot 진입점.
 */
@SpringBootApplication
public class WeatherhoodServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherhoodServerApplication.class, args);
    }

}
