package com.weatherhood.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 테스트 서버 문서는 별도 HTTPS API 호스트를 호출하며 문서 인증 정보를 전달하지 않는다. 문서 호스트 자체의 Basic Auth와 관리 경로 차단은 NPM에서 수행한다.
 */
@Configuration
@Profile("dev")
public class DevApiDocumentationConfiguration implements WebMvcConfigurer {
    /**
     * Spring이 개발 환경의 API 문서·CORS 구성을 등록할 때 사용하는 생성자.
     */
    public DevApiDocumentationConfiguration() {
    }

    private static final String API_URL = "https://weatherhood-dev-api.ansandy.co.kr";
    private static final String DOCUMENTATION_ORIGIN = "https://weatherhood-dev-docs.ansandy.co.kr";

    /**
     * 프록시 내부 HTTP 주소 대신 공개된 테스트 API 주소를 명세에 사용한다.
     *
     * @return Swagger 실행 대상이 지정된 명세
     */
    @Bean
    public OpenAPI devOpenApi() {
        return new OpenAPI().addServersItem(new Server().url(API_URL).description("Weatherhood dev API"));
    }

    /**
     * 문서 화면의 API 테스트와 명시적인 Bearer 헤더를 허용하며 교차 출처 쿠키는 허용하지 않는다.
     *
     * @param registry MVC의 CORS 등록 대상
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(DOCUMENTATION_ORIGIN)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(false);
    }
}
