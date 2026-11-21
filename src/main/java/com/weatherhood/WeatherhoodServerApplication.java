package com.weatherhood;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * Weatherhood 서버의 Spring Boot 진입점.
 */
@SpringBootApplication
public class WeatherhoodServerApplication {
    /**
     * Spring Boot가 애플리케이션 구성을 등록할 때 사용하는 생성자.
     */
    public WeatherhoodServerApplication() {
    }


    /**
     * Spring Boot 서버를 기동한다.
     *
     * @param args Spring Boot 실행 인수
     */
    public static void main(String[] args) {
        SpringApplication.run(WeatherhoodServerApplication.class, args);
    }

}
