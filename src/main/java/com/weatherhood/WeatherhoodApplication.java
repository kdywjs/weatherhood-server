package com.weatherhood;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WeatherhoodApplication {
    public static void main(String[] args) {
        SpringApplication.run(WeatherhoodApplication.class, args);
    }
}
