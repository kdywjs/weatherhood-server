package com.weatherhood.today.client;

import com.weatherhood.today.dto.ExactPrecipitation;
import com.weatherhood.today.dto.PollutantReading;
import com.weatherhood.today.dto.Precipitation;
import com.weatherhood.today.dto.PrecipitationRange;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.dto.WeatherSource;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 공급처의 코드·숫자 문자열·시간 표기를 내부 자료로 변환하는 유틸리티.
 *
 * <p>알 수 없는 값은 null 또는 누락 항목으로 처리한다. 정상적인 0과 강수 없음을 결측으로 간주하지 않는다.
 */
public final class WeatherNormalizer {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private WeatherNormalizer() {
    }

    /**
     * 유한한 숫자를 허용 범위 안에서만 파싱한다.
     *
     * @param raw 공급 숫자 문자열, null 허용
     * @param min 허용 최솟값(포함)
     * @param max 허용 최댓값(포함)
     * @return 파싱된 값, 숫자가 아니거나 범위 밖이면 null
     */
    public static Double parseNumberInRange(String raw, double min, double max) {
        if (raw == null) {
            return null;
        }
        try {
            double number = Double.parseDouble(raw);
            return Double.isFinite(number) && number >= min && number <= max ? number : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 공급 날짜·시간을 한국 시각으로 변환하고 2400은 다음 날 00시로 정규화한다.
     *
     * @param date yyyyMMdd 형식 날짜
     * @param time HHmm 형식 시각 또는 2400
     * @return 오프셋이 포함된 한국 시각
     * @throws DateTimeException 날짜 또는 시각 형식이 유효하지 않은 경우
     */
    public static OffsetDateTime parseProviderDateTime(String date, String time) {
        LocalDate dateValue = LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        if (time.equals("2400")) {
            return dateValue.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        }
        return dateValue.atTime(LocalTime.parse(time, DateTimeFormatter.ofPattern("HHmm"))).atZone(KST)
                .toOffsetDateTime();
    }

    /**
     * 기상청 강수량을 수치·범위로 변환한다. 실황의 결측을 강수 없음으로 바꾸지 않는다.
     *
     * @param source 실황 또는 예보 종류
     * @param raw    공급 문자열; JSON null과 항목 부재는 null로 전달
     * @param start  집계 시작 시각
     * @param end    집계 종료 시각
     * @return 유효한 강수량, 결측·미지원 표현·잘못된 구간이면 null
     */
    public static Precipitation parsePrecipitation(WeatherSource source, String raw, OffsetDateTime start,
                                                   OffsetDateTime end) {
        if (raw == null || start == null || end == null || !start.isBefore(end) || (source == WeatherSource.AIR
                || source == WeatherSource.SUN)) {
            return null;
        }
        String normalized = raw.trim().replace(" ", "");
        if (source == WeatherSource.OBSERVATION) {
            Double amount = parseNumberInRange(normalized, 0, Math.nextDown(900.0));
            return amount == null ? null : new ExactPrecipitation(amount, start, end);
        }
        if (normalized.equals("강수없음") || normalized.equals("-")) {
            return new ExactPrecipitation(0, start, end);
        }
        if (normalized.matches("[0-9]+(?:\\.[0-9]+)?(?:mm)?")) {
            Double amount = parseNumberInRange(normalized.replace("mm", ""), 0, Math.nextDown(900.0));
            if (amount == null) {
                return null;
            }
            // 예보의 30 이상 수치는 가이드의 범주값이다. 실황의 수치와 구분한다.
            if (amount >= 50) {
                return new PrecipitationRange(50, null, true, false, start, end);
            }
            if (amount >= 30) {
                return new PrecipitationRange(30, 50.0, true, false, start, end);
            }
            return new ExactPrecipitation(amount, start, end);
        }
        return switch (normalized) {
            case "1mm미만", "1.0mm미만" -> new PrecipitationRange(0, 1.0, true, false, start, end);
            case "1~29mm", "1.0~29.0mm" -> new PrecipitationRange(1, 30.0, true, false, start, end);
            case "30~50mm", "30.0~50.0mm" -> new PrecipitationRange(30, 50.0, true, false, start, end);
            case "50mm이상", "50.0mm이상" -> new PrecipitationRange(50, null, true, false, start, end);
            default -> null;
        };
    }

    public static List<WeatherDatum> normalizeKmaItems(WeatherSource source, String location, JsonNode items) {
        List<WeatherDatum> result = new ArrayList<>();
        for (JsonNode item : items) {
            try {
                OffsetDateTime issued = parseProviderDateTime(item.path("baseDate").asString(),
                        item.path("baseTime").asString());
                OffsetDateTime target = source == WeatherSource.OBSERVATION ? issued
                        : parseProviderDateTime(item.path("fcstDate").asString(), item.path("fcstTime").asString());
                JsonNode rawNode = item.path(source == WeatherSource.OBSERVATION ? "obsrValue" : "fcstValue");
                String raw = rawNode.isNull() || rawNode.isMissingNode() ? null : rawNode.asString();
                String category = item.path("category").asString();
                WeatherField field = resolveField(source, category);
                if (field == null) {
                    continue;
                }
                Object value = normalizeValue(source, field, raw, target);
                if (value != null) {
                    result.add(new WeatherDatum(source, location, field, target, issued, value));
                }
            } catch (DateTimeException ignored) {
                // A malformed item must not discard valid sibling items.
            }
        }
        return result;
    }

    public static List<WeatherDatum> normalizeAirQualityItems(String station, JsonNode items) {
        List<WeatherDatum> result = new ArrayList<>();
        for (JsonNode item : items) {
            try {
                String measurementTime = item.path("dataTime").asString();
                OffsetDateTime end = parseProviderDateTime(measurementTime.substring(0, 10).replace("-", ""),
                        measurementTime.substring(11, 16).replace(":", ""));
                for (String key : List.of("pm10", "pm25")) {
                    String flag = item.path(key + "Flag").asString("");
                    if (!flag.isBlank()) {
                        continue;
                    }
                    Double number = parseNumberInRange(item.path(key + "Value").asString(), 0, 100000);
                    String grade = switch (item.path(key + "Grade1h").asString()) {
                        case "1" -> "good";
                        case "2" -> "moderate";
                        case "3" -> "bad";
                        case "4" -> "very_bad";
                        default -> null;
                    };
                    if (number != null || grade != null) {
                        result.add(new WeatherDatum(WeatherSource.AIR, station,
                                key.equals("pm10") ? WeatherField.PM10 : WeatherField.PM25, end, end,
                                new PollutantReading(number, grade, end.minusHours(1), end)));
                    }
                }
            } catch (DateTimeException | IndexOutOfBoundsException ignored) {
                // Skip only the malformed measurement; preserve the rest of the response.
            }
        }
        return result;
    }

    private static WeatherField resolveField(WeatherSource source, String category) {
        boolean supported = switch (source) {
            case OBSERVATION -> List.of("T1H", "PTY", "RN1", "WSD", "REH").contains(category);
            case ULTRA -> List.of("T1H", "SKY", "PTY", "RN1", "WSD", "REH", "POP").contains(category);
            case SHORT -> List.of("TMP", "SKY", "PTY", "PCP", "WSD", "REH", "POP", "TMN", "TMX")
                    .contains(category);
            case AIR, SUN -> false;
        };
        if (!supported) {
            return null;
        }
        return switch (category) {
            case "T1H", "TMP" -> WeatherField.TEMPERATURE;
            case "SKY" -> WeatherField.SKY;
            case "PTY" -> WeatherField.PRECIPITATION_TYPE;
            case "RN1", "PCP" -> WeatherField.PRECIPITATION_AMOUNT;
            case "WSD" -> WeatherField.WIND;
            case "REH" -> WeatherField.HUMIDITY;
            case "POP" -> WeatherField.PROBABILITY;
            case "TMN" -> WeatherField.MIN;
            case "TMX" -> WeatherField.MAX;
            default -> null;
        };
    }

    private static Object normalizeValue(WeatherSource source, WeatherField field, String raw, OffsetDateTime target) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        raw = raw.trim();
        return switch (field) {
            case TEMPERATURE, MIN, MAX -> parseNumberInRange(raw, -100, 70);
            case WIND -> parseNumberInRange(raw, 0, 150);
            case HUMIDITY, PROBABILITY -> parseNumberInRange(raw, 0, 100);
            case SKY -> switch (raw) {
                case "1" -> "clear";
                case "3" -> "partly_cloudy";
                case "4" -> "cloudy";
                default -> null;
            };
            case PRECIPITATION_TYPE -> switch (raw) {
                case "0" -> "none";
                case "1" -> "rain";
                case "2" -> "rain_snow";
                case "3" -> "snow";
                case "4" -> "shower";
                case "5" -> source == WeatherSource.SHORT ? null : "drizzle";
                case "6" -> source == WeatherSource.SHORT ? null : "drizzle_snow_flurries";
                case "7" -> source == WeatherSource.SHORT ? null : "snow_flurries";
                default -> null;
            };
            // 기상청 1시간 강수량의 대상 시각은 집계 종료 시각이다. 근거는 기능 설계 문서를 참조한다.
            case PRECIPITATION_AMOUNT -> parsePrecipitation(source, raw, target.minusHours(1), target);
            default -> null;
        };
    }
}
