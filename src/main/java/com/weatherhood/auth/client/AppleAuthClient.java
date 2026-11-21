package com.weatherhood.auth.client;

import com.weatherhood.auth.dto.AppleTokens;
import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * native iOS 인증 코드 교환·refresh 검증·폐기를 수행한다. 요청·응답과 예외 원문은 기록하지 않는다.
 */
@Component
public class AppleAuthClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final AppleClientSecret secret;
    private final JsonMapper json;
    private final String baseUrl;
    private final String clientId;

    /**
     * 의존 객체와 외부 설정을 받아 AppleAuthClient를 구성한다.
     *
     * @param secret   Apple client_secret 서명기
     * @param json     외부 JSON 응답 변환기
     * @param baseUrl  공급처 API 기본 주소
     * @param clientId 허용할 native iOS 앱 Bundle ID
     */
    public AppleAuthClient(AppleClientSecret secret, JsonMapper json,
                           @Value("${auth.apple.base-url:https://appleid.apple.com}") String baseUrl,
                           @Value("${auth.apple.client-id:co.kr.ansandy.weatherhood}") String clientId) {
        this.secret = secret;
        this.json = json;
        this.baseUrl = baseUrl;
        this.clientId = clientId;
    }

    /**
     * 일회용 Apple 코드를 교환하고 사용자 식별 토큰과 refresh token을 얻는다.
     *
     * @param code Apple이 발급한 일회용 인증 코드
     * @return 검증할 식별 토큰과 보관할 refresh token
     */
    public AppleTokens exchangeAuthorizationCode(String code) {
        JsonNode node = postForm("/auth/token", Map.of("grant_type", "authorization_code", "code", code));
        return new AppleTokens(requiredText(node, "id_token"), requiredText(node, "refresh_token"));
    }

    /**
     * 저장한 refresh token으로 Apple 자격의 유효성을 확인한다.
     *
     * @param refreshToken Apple 세션 검증·폐기에 사용할 원문 refresh token
     * @return Apple이 새로 발급한 사용자 식별 JWT
     */
    public String validateRefreshToken(String refreshToken) {
        return requiredText(postForm("/auth/token", Map.of("grant_type", "refresh_token",
                "refresh_token", refreshToken)), "id_token");
    }

    /**
     * Apple 연결 해제를 위해 저장한 refresh token을 폐기한다.
     *
     * @param refreshToken Apple 세션 검증·폐기에 사용할 원문 refresh token
     */
    public void revokeRefreshToken(String refreshToken) {
        postForm("/auth/revoke", Map.of("token", refreshToken, "token_type_hint", "refresh_token"));
    }

    private JsonNode postForm(String path, Map<String, String> values) {
        Map<String, String> form = new LinkedHashMap<>(values);
        form.put("client_id", clientId);
        form.put("client_secret", secret.createClientSecret());
        String body = form.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
            }
            if (response.statusCode() == 400 && path.equals("/auth/token")) {
                JsonNode error = json.readTree(response.body());
                if ("invalid_grant".equals(error.path("error").asString())) {
                    throw new ApplicationException(ErrorCode.INVALID_APPLE_CREDENTIAL);
                }
            }
            throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
        } catch (IOException | JacksonException exception) {
            throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString() || value.asString().isBlank()) {
            throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
        }
        return value.asString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
