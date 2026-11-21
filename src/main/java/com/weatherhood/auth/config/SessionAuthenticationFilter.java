package com.weatherhood.auth.config;

import com.weatherhood.auth.service.SessionService;
import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 개인 API만 Bearer 인증한다. 공개 날씨·지역 조회에는 사용자 세션을 요구하지 않는다.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final HandlerExceptionResolver errors;

    /**
     * 의존 객체와 외부 설정을 받아 SessionAuthenticationFilter를 구성한다.
     *
     * @param sessions 세션 저장 또는 인증 서비스
     * @param errors   필터 오류를 공통 응답으로 변환할 처리기
     */
    public SessionAuthenticationFilter(SessionService sessions, HandlerExceptionResolver errors) {
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "OPTIONS".equals(request.getMethod()) || !(path.equals("/api/v1/me")
                || path.startsWith("/api/v1/me/") || path.equals("/api/v1/auth/logout"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            List<String> headers = Collections.list(request.getHeaders("Authorization"));
            if (headers.size() != 1 || !headers.getFirst().regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new ApplicationException(ErrorCode.UNAUTHENTICATED);
            }
            var identity = sessions.authenticateToken(headers.getFirst().substring(7));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity, null, List.of()));
            SecurityContextHolder.setContext(context);
        } catch (Exception exception) {
            errors.resolveException(request, response, null, exception);
            return;
        }
        chain.doFilter(request, response);
    }
}
