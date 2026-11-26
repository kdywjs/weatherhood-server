package com.weatherhood.today.client;

import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.exception.WeatherProviderException.Reason;
import com.weatherhood.today.exception.WeatherProviderException;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 기상청과 에어코리아의 HTTP 호출 및 공급 응답 검증 구현.
 *
 * <p>개별 호출은 2초로 제한하고 리다이렉트를 따르지 않는다.
 * 인증키가 포함된 URL이나 공급처 오류 본문을 예외 메시지에 포함하지 않는다.
 */
@Component
public class HttpWeatherProvider implements WeatherProvider {
    private static final Logger LOG = LoggerFactory.getLogger(HttpWeatherProvider.class);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final KasiSunTimesClient sunTimesClient;
    private final JsonMapper jsonMapper;
    private final String kmaKey;
    private final String airKey;
    private final String kmaUrl;
    private final String airUrl;

    /**
     * 의존 객체와 외부 설정을 받아 HttpWeatherProvider를 구성한다.
     *
     * @param jsonMapper     날씨 자료 JSON 변환기
     * @param kmaKey         기상청 API허브 인증키
     * @param airKey         에어코리아 서비스 키
     * @param kmaUrl         기상청 API 기본 주소
     * @param airUrl         에어코리아 API 기본 주소
     * @param sunTimesClient 일출·일몰 조회 클라이언트
     */
    public HttpWeatherProvider(JsonMapper jsonMapper, @Value("${KMA_API_HUB_KEY:}") String kmaKey,
                               @Value("${AIRKOREA_SERVICE_KEY:}") String airKey,
                               @Value("${weather.kma-url:https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/}") String kmaUrl,
                               @Value("${weather.air-url:https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/}") String airUrl,
                               KasiSunTimesClient sunTimesClient) {
        this.sunTimesClient = sunTimesClient;
        this.jsonMapper = jsonMapper;
        this.kmaKey = kmaKey;
        this.airKey = airKey;
        this.kmaUrl = kmaUrl;
        this.airUrl = airUrl;
    }

    @Override
    public Result fetchWeatherData(Request request) {
        if (request.source() == WeatherSource.SUN) {
            return sunTimesClient.fetchSunTimes(request);
        }
        boolean airQualityRequest = request.source() == WeatherSource.AIR;
        String key = airQualityRequest ? airKey : kmaKey;
        if (key.isBlank()) {
            LOG.warn("Weather credential missing source={} release={}", request.source(), request.release());
            throw new WeatherProviderException(Reason.AUTHENTICATION, true, 0);
        }
        URI requestUri = buildRequestUri(request, airQualityRequest);
        long start = System.nanoTime();
        int status = 0;
        String resultCode = "-";
        try {
            var response = client.send(HttpRequest.newBuilder(requestUri).timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
            if (status != 200) {
                long retry = parseRetryAfterSeconds(response.headers().firstValue("Retry-After").orElse(""));
                throw new WeatherProviderException(
                        response.statusCode() == 401 || response.statusCode() == 403
                                ? Reason.AUTHENTICATION : Reason.UNAVAILABLE, true, retry);
            }
            var root = jsonMapper.readTree(response.body()).path("response");
            String code = root.path("header").path("resultCode").asString("");
            resultCode = code.matches("[0-9]{2}") ? code : "unknown";
            var result = parseResponse(request, airQualityRequest, root, code);
            if (!result.complete()) {
                logUnsuccessfulCall(request, start, status, resultCode, "INCOMPLETE");
            }
            return result;
        } catch (WeatherProviderException exception) {
            logUnsuccessfulCall(request, start, status, resultCode, exception.reason().name());
            throw exception;
        } catch (InterruptedException exception) {
            logUnsuccessfulCall(request, start, status, resultCode, "INTERRUPTED");
            Thread.currentThread().interrupt();
            throw new WeatherProviderException(Reason.INTERRUPTED, false, 300);
        } catch (HttpTimeoutException exception) {
            logUnsuccessfulCall(request, start, status, resultCode, "TIMEOUT");
            // One slow forecast must not suppress unrelated regions or observation requests.
            throw new WeatherProviderException(Reason.UNAVAILABLE, false, 300);
        } catch (IOException exception) {
            logUnsuccessfulCall(request, start, status, resultCode, "IO_ERROR");
            throw new WeatherProviderException(Reason.UNAVAILABLE, true, 300);
        } catch (JacksonException exception) {
            logUnsuccessfulCall(request, start, status, resultCode, "INVALID_RESPONSE");
            throw new WeatherProviderException(Reason.INVALID_RESPONSE, true, 300);
        }
    }

    private void logUnsuccessfulCall(Request request, long start, int status, String resultCode, String reason) {
        LOG.warn("Weather collection source={} location={} release={} reason={} elapsedMs={} " +
                        "httpStatus={} resultCode={}",
                request.source(), request.location(), request.release(), reason,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), status, resultCode);
    }

    static long parseRetryAfterSeconds(String value) {
        try {
            return Math.max(300, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            try {
                return Math.max(300, Duration.between(Instant.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds());
            } catch (DateTimeParseException ignored) {
                return 300;
            }
        }
    }

    private URI buildRequestUri(Request request, boolean airQualityRequest) {
        Map<String, String> parameters = new LinkedHashMap<>();
        String path;
        if (airQualityRequest) {
            path = airUrl + "getMsrstnAcctoRltmMesureDnsty";
            parameters.put("serviceKey",
                    airKey.contains("%") ? URLDecoder.decode(airKey, StandardCharsets.UTF_8) : airKey);
            parameters.put("returnType", "json");
            parameters.put("stationName", request.location());
            parameters.put("dataTerm", "DAILY");
            parameters.put("ver", "1.4");
            parameters.put("numOfRows", "100");
            parameters.put("pageNo", "1");
        } else {
            path = kmaUrl + switch (request.source()) {
                case OBSERVATION -> "getUltraSrtNcst";
                case ULTRA -> "getUltraSrtFcst";
                default -> "getVilageFcst";
            };
            parameters.put("authKey", kmaKey);
            parameters.put("dataType", "JSON");
            parameters.put("numOfRows", "2000");
            parameters.put("pageNo", "1");
            parameters.put("base_date", request.release().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE));
            parameters.put("base_time", request.release().format(DateTimeFormatter.ofPattern("HHmm")));
            String[] grid = request.location().split(",");
            parameters.put("nx", grid[0]);
            parameters.put("ny", grid[1]);
        }
        String query = parameters.entrySet().stream()
                .map(parameter -> URLEncoder.encode(parameter.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(parameter.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        return URI.create(path + "?" + query);
    }

    private Result parseResponse(Request request, boolean airQualityRequest, JsonNode root, String code) {
        if (!code.equals("00")) {
            throw new WeatherProviderException(Set.of("20", "21", "30", "31", "32").contains(code)
                    ? Reason.AUTHENTICATION : Reason.UNAVAILABLE,
                    !Set.of("03", "02").contains(code), 300);
        }
        var body = root.path("body");
        var items = airQualityRequest ? body.path("items") : body.path("items").path("item");
        if (!items.isArray()) {
            throw new WeatherProviderException(Reason.INVALID_RESPONSE, false, 300);
        }
        List<WeatherDatum> values =
                airQualityRequest ? WeatherNormalizer.normalizeAirQualityItems(request.location(), items)
                        : WeatherNormalizer.normalizeKmaItems(request.source(), request.location(), items);
        if (!airQualityRequest) {
            values = values.stream().filter(datum -> datum.issued().equals(request.release())
                    || request.source() == WeatherSource.ULTRA && datum.issued()
                    .equals(request.release().minusMinutes(30))).toList();
        }
        boolean expected = values.stream()
                .anyMatch(datum -> airQualityRequest ? !datum.target().isBefore(request.release()) : true);
        return new Result(values, expected && body.path("totalCount").asInt() == items.size());
    }
}
