package com.weatherhood.today.client;

import com.weatherhood.today.client.WeatherProvider.Request;
import com.weatherhood.today.client.WeatherProvider.Result;
import com.weatherhood.today.dto.SunEvent;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.exception.WeatherProviderException.Reason;
import com.weatherhood.today.exception.WeatherProviderException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 한국천문연구원의 위치별 출몰시각 조회. 요청 좌표와 공급 기준 지역을 구분한다.
 *
 * <p>일일 재사용·중복 수집 제어는 수집기에서 담당한다. XML 외부 엔티티를 허용하지 않고
 * 원문·인증키가 포함될 수 있는 예외 메시지는 전달하지 않는다.
 */
@Component
public class KasiSunTimesClient {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final String serviceKey;
    private final String baseUrl;

    /**
     * 의존 객체와 외부 설정을 받아 KasiSunTimesClient를 구성한다.
     *
     * @param serviceKey 한국천문연구원 서비스 키
     * @param baseUrl    공급처 API 기본 주소
     */
    public KasiSunTimesClient(@Value("${KASI_SERVICE_KEY:}") String serviceKey,
                              @Value("${weather.kasi-url:https://apis.data.go.kr/B090041/openapi/service/RiseSetInfoService/}") String baseUrl) {
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
    }

    /**
     * 행정동 대표 좌표에 대응하는 기준 지역의 하루 일출·일몰을 조회한다.
     *
     * @param request SUN 자료, 위도·경도 문자열, 요청 날짜의 한국 자정
     * @return 개별 누락을 허용한 결과; 일출·일몰이 모두 유효할 때 complete=true
     * @throws WeatherProviderException 인증·통신·응답 검증 실패 시
     */
    public Result fetchSunTimes(Request request) {
        if (serviceKey.isBlank()) {
            throw new WeatherProviderException(Reason.AUTHENTICATION, true, 0);
        }
        try {
            var response = client.send(HttpRequest.newBuilder(buildRequestUri(request))
                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new WeatherProviderException(response.statusCode() == 401 || response.statusCode() == 403
                        ? Reason.AUTHENTICATION : Reason.UNAVAILABLE, true,
                        HttpWeatherProvider.parseRetryAfterSeconds(
                                response.headers().firstValue("Retry-After").orElse("")));
            }
            return parseResponse(request, response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WeatherProviderException(Reason.INTERRUPTED, false, 300);
        } catch (IOException exception) {
            throw new WeatherProviderException(Reason.UNAVAILABLE, true, 300);
        }
    }

    private URI buildRequestUri(Request request) {
        String[] coordinates = request.location().split(",");
        if (request.source() != WeatherSource.SUN || coordinates.length != 2
                || WeatherNormalizer.parseNumberInRange(coordinates[0], -90, 90) == null
                || WeatherNormalizer.parseNumberInRange(coordinates[1], -180, 180) == null) {
            throw new WeatherProviderException(Reason.INVALID_RESPONSE, false, 300);
        }
        String key = serviceKey.contains("%") ? URLDecoder.decode(serviceKey, StandardCharsets.UTF_8) : serviceKey;
        return URI.create(baseUrl + "getLCRiseSetInfo?serviceKey=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "&locdate=" + request.release().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&latitude=" + coordinates[0] + "&longitude=" + coordinates[1] + "&dnYn=Y");
    }

    private Result parseResponse(Request request, String body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Element root = builder.parse(new InputSource(new StringReader(body))).getDocumentElement();
            String code = findText(root, "resultCode");
            if (!code.equals("00")) {
                String errorCode = code.isEmpty() ? findText(root, "returnReasonCode") : code;
                throw new WeatherProviderException(Set.of("20", "21", "30", "31", "32").contains(errorCode)
                        ? Reason.AUTHENTICATION : Reason.UNAVAILABLE,
                        !Set.of("02", "03").contains(errorCode), 300);
            }
            var items = root.getElementsByTagName("item");
            if (items.getLength() == 0) {
                return new Result(List.of(), false);
            }
            if (items.getLength() != 1) {
                throw new WeatherProviderException(Reason.INVALID_RESPONSE, false, 300);
            }
            Element item = (Element) items.item(0);
            LocalDate date = LocalDate.parse(findText(item, "locdate"), DateTimeFormatter.BASIC_ISO_DATE);
            if (!date.equals(request.release().toLocalDate())) {
                throw new WeatherProviderException(Reason.INVALID_RESPONSE, false, 300);
            }
            OffsetDateTime sunrise = parseEventTime(date, findText(item, "sunrise"));
            OffsetDateTime sunset = parseEventTime(date, findText(item, "sunset"));
            if (sunrise != null && sunset != null && !sunrise.isBefore(sunset)) {
                throw new WeatherProviderException(Reason.INVALID_RESPONSE, false, 300);
            }
            List<WeatherDatum> values = new ArrayList<>();
            addEvent(values, request, item, WeatherField.SUNRISE, sunrise);
            addEvent(values, request, item, WeatherField.SUNSET, sunset);
            return new Result(values, values.size() == 2);
        } catch (ParserConfigurationException | SAXException | IOException | DateTimeException exception) {
            throw new WeatherProviderException(Reason.INVALID_RESPONSE, false, 300);
        }
    }

    private OffsetDateTime parseEventTime(LocalDate date, String raw) {
        if (!raw.matches("[0-9]{4}(?:[0-9]{2})?")) {
            return null;
        }
        try {
            int hour = Integer.parseInt(raw.substring(0, 2));
            int minute = Integer.parseInt(raw.substring(2, 4));
            int second = raw.length() == 6 ? Integer.parseInt(raw.substring(4, 6)) : 0;
            return date.atTime(LocalTime.of(hour, minute, second)).atZone(KST).toOffsetDateTime();
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private void addEvent(List<WeatherDatum> values, Request request, Element item, WeatherField field,
                          OffsetDateTime time) {
        if (time != null) {
            SunEvent event = new SunEvent(time, findText(item, "location"),
                    WeatherNormalizer.parseNumberInRange(findText(item, "latitudeNum"), -90, 90),
                    WeatherNormalizer.parseNumberInRange(findText(item, "longitudeNum"), -180, 180));
            values.add(new WeatherDatum(WeatherSource.SUN, request.location(), field, request.release(),
                    request.release(), event));
        }
    }

    private String findText(Element element, String tag) {
        var values = element.getElementsByTagName(tag);
        return values.getLength() == 0 ? "" : values.item(0).getTextContent().trim();
    }
}
