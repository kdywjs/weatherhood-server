package com.weatherhood.auth.config;

import com.weatherhood.auth.service.SessionService;
import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 쿠키·폼 로그인 없이 명시적 Bearer 헤더로 개인 API를 보호한다.
 */
@Configuration
@EnableScheduling
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer")
public class SecurityConfiguration {
    /**
     * Spring이 Bearer 인증 구성을 등록할 때 사용하는 생성자.
     */
    public SecurityConfiguration() {
    }

    /**
     * 비밀번호 로그인용 기본 계정과 자동 생성 비밀번호를 만들지 않는다.
     */
    @Bean
    UserDetailsService disabledPasswordUsers() {
        return username -> {
            throw new UsernameNotFoundException("Password login is not supported");
        };
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SessionService sessions,
                                    @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors)
            throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/v1/me", "/api/v1/me/**", "/api/v1/auth/logout").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(handler -> handler.authenticationEntryPoint((request, response, exception) ->
                        errors.resolveException(request, response, null,
                                new ApplicationException(ErrorCode.UNAUTHENTICATED))))
                .addFilterBefore(new SessionAuthenticationFilter(sessions, errors), AnonymousAuthenticationFilter.class)
                .build();
    }
}
