package com.weatherhood.region.client;

import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 좌표에 속한 행정동(H) 코드만 반환한다. GPS·키·공급처 원문은 보관하거나 로그에 남기지 않는다.
 */
@Component
public class KakaoRegionClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String apiKey;
    private final String baseUrl;
    private final JsonMapper json;

    /**
     * 의존 객체와 외부 설정을 받아 KakaoRegionClient를 구성한다.
     *
     * @param apiKey  카카오 REST API 키
     * @param baseUrl 공급처 API 기본 주소
     * @param json    외부 JSON 응답 변환기
     */
    public KakaoRegionClient(@Value("${location.kakao.api-key:}") String apiKey,
                             @Value("${location.kakao.base-url:https://dapi.kakao.com}") String baseUrl,
                             JsonMapper json) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.json = json;
    }

    /**
     * 카카오 응답에서 행정동 H 코드만 선택한다.
     *
     * @param latitude  WGS84 위도(도)
     * @param longitude WGS84 경도(도)
     * @return 변환한 토큰 문자열
     */
    public String fetchAdministrativeCode(double latitude, double longitude) {
        if (apiKey.isBlank()) {
            throw new ApplicationException(ErrorCode.LOCATION_UNAVAILABLE);
        }
        var uri = URI.create(baseUrl + "/v2/local/geo/coord2regioncode.json?input_coord=WGS84&x="
                + longitude + "&y=" + latitude);
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Authorization", "KakaoAK " + apiKey).GET().build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ApplicationException(ErrorCode.LOCATION_UNAVAILABLE);
            }
            var documents = json.readTree(response.body()).path("documents");
            if (!documents.isArray()) {
                throw new ApplicationException(ErrorCode.LOCATION_UNAVAILABLE);
            }
            for (var document : documents) {
                if ("H".equals(document.path("region_type").asString())) {
                    String code = document.path("code").asString();
                    if (!code.matches("[0-9]{10}")) {
                        throw new ApplicationException(ErrorCode.LOCATION_UNAVAILABLE);
                    }
                    return code;
                }
            }
            throw new ApplicationException(ErrorCode.LOCATION_NOT_FOUND);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApplicationException(ErrorCode.LOCATION_UNAVAILABLE);
        } catch (IOException | JacksonException exception) {
            throw new ApplicationException(ErrorCode.LOCATION_UNAVAILABLE);
        }
    }
}
