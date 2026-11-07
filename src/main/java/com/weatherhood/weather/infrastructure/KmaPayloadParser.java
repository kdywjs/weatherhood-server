package com.weatherhood.weather.infrastructure;

import com.weatherhood.weather.domain.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class KmaPayloadParser {
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private final JsonMapper json;
    public KmaPayloadParser(JsonMapper json) { this.json = json; }

    public record Page(int totalCount, int pageNumber, List<Map<?, ?>> items) {}

    public Page page(String text) {
        try {
            Map<?, ?> root = json.readValue(text, Map.class);
            Map<?, ?> response = object(root.get("response"));
            if (!"00".equals(String.valueOf(object(response.get("header")).get("resultCode"))))
                throw WeatherException.unavailable();
            Map<?, ?> body = object(response.get("body"));
            int total = Integer.parseInt(body.get("totalCount").toString());
            int page = Integer.parseInt(body.get("pageNo").toString());
            int pageSize = Integer.parseInt(body.get("numOfRows").toString());
            if (total <= 0 || total > 20000) throw WeatherException.unavailable();
            Object raw = object(body.get("items")).get("item");
            List<?> rows = raw instanceof List<?> list ? list : List.of(object(raw));
            if (rows.size() > pageSize || page < 1) throw WeatherException.unavailable();
            return new Page(total, page, rows.stream().map(KmaPayloadParser::object).toList());
        } catch (RuntimeException error) {
            throw WeatherException.unavailable();
        }
    }

    public WeatherBatch batch(Grid grid, WeatherSource source, ZonedDateTime publication,
                              ZonedDateTime fetched, List<Map<?, ?>> items) {
        Map<ZonedDateTime, Map<String, String>> grouped = new TreeMap<>();
        try {
            for (Map<?, ?> item : items) {
                if (Integer.parseInt(item.get("nx").toString()) != grid.x()
                        || Integer.parseInt(item.get("ny").toString()) != grid.y()
                        || !publication.toLocalDate().format(DAY).equals(item.get("baseDate").toString())
                        || !publication.format(DateTimeFormatter.ofPattern("HHmm")).equals(item.get("baseTime").toString()))
                    throw WeatherException.unavailable();
                ZonedDateTime target = source == WeatherSource.OBSERVATION ? publication
                        : time(item.get("fcstDate").toString(), item.get("fcstTime").toString(), publication);
                String value = item.get(source == WeatherSource.OBSERVATION ? "obsrValue" : "fcstValue").toString();
                String category = item.get("category").toString();
                String previous = grouped.computeIfAbsent(target, ignored -> new HashMap<>()).put(category, value);
                if (previous != null) throw WeatherException.unavailable();
            }
            List<WeatherBatch.Sample> samples = grouped.entrySet().stream()
                    .map(entry -> sample(source, publication, entry.getKey(), entry.getValue())).toList();
            if (samples.isEmpty() || samples.stream().noneMatch(s -> s.values().hasWeather()))
                throw WeatherException.unavailable();
            return new WeatherBatch(grid, source, publication, fetched, samples);
        } catch (RuntimeException error) { throw WeatherException.unavailable(); }
    }

    private WeatherBatch.Sample sample(WeatherSource source, ZonedDateTime publication, ZonedDateTime time,
                                       Map<String, String> raw) {
        int step = source.stepMinutes(publication, time);
        boolean extended = source == WeatherSource.SHORT_TERM_FORECAST && step == 180;
        String pty = switch (raw.getOrDefault("PTY", "")) {
            case "0" -> "none";
            case "1", "4" -> "rain";
            case "2" -> "rain_snow";
            case "3" -> "snow";
            case "5" -> source == WeatherSource.SHORT_TERM_FORECAST ? null : "drizzle";
            case "6" -> source == WeatherSource.SHORT_TERM_FORECAST ? null : "drizzle_snow_flurries";
            case "7" -> source == WeatherSource.SHORT_TERM_FORECAST ? null : "snow_flurries";
            default -> null;
        };
        String sky = switch (raw.getOrDefault("SKY", "")) {
            case "1" -> "clear";
            case "3" -> "partly_cloudy";
            case "4" -> "cloudy";
            default -> null;
        };
        String rain = raw.getOrDefault("PCP", raw.get("RN1"));
        WeatherValues values = new WeatherValues(number(raw.getOrDefault("TMP", raw.get("T1H")), -100, 100),
                sky, pty, number(raw.get("POP"), 0, 100),
                extended ? null : number(raw.get("WSD"), 0, 200), number(raw.get("REH"), 0, 100),
                precipitation(rain, extended, time), number(raw.get("TMN"), -100, 100),
                number(raw.get("TMX"), -100, 100), extended ? intensity(raw.get("WSD"), false) : null);
        return new WeatherBatch.Sample(time, step, values);
    }

    private static String intensity(String raw, boolean rain) {
        if (raw == null) return null;
        return switch (raw.replace("*", "").trim()) {
            case "0" -> rain ? "none" : null;
            case "1" -> "light";
            case "2" -> "moderate";
            case "3" -> "strong";
            default -> null;
        };
    }

    static WeatherValues.Precipitation precipitation(String raw, boolean extended, ZonedDateTime time) {
        if (raw == null) return null;
        ZonedDateTime start = time.minusHours(1);
        String value = raw.trim().replace("mm", "").replace(" ", "");
        if (extended) {
            String label = intensity(value, true);
            return label == null ? null : new WeatherValues.Precipitation("qualitative", null, null, null,
                    null, null, label, "mm", start, time);
        }
        if (value.equals("강수없음") || value.equals("없음")) value = "0";
        try {
            if (value.endsWith("미만")) {
                Double upper = number(value.replace("미만", ""), 0, 899);
                return upper == null || upper == 0 ? null : new WeatherValues.Precipitation("less_than", null, null,
                        upper, null, false, null, "mm", start, time);
            }
            if (value.endsWith("이상")) {
                Double lower = number(value.replace("이상", ""), 0, 899);
                return lower == null ? null : new WeatherValues.Precipitation("range", null,
                        lower, null, true, null, null, "mm", start, time);
            }
            if (value.contains("~")) {
                String[] bounds = value.split("~");
                if (bounds.length != 2) return null;
                Double lower = number(bounds[0], 0, 899);
                Double upper = number(bounds[1], 0, 899);
                return lower == null || upper == null || lower >= upper ? null : new WeatherValues.Precipitation(
                        "range", null, lower, upper, true, false, null, "mm", start, time);
            }
            Double exact = number(value, 0, 899);
            if (exact != null && !raw.contains("mm") && exact >= 30) {
                double lower = exact < 50 ? 30 : 50;
                return new WeatherValues.Precipitation("range", null, lower, exact < 50 ? 50.0 : null,
                        true, exact < 50 ? false : null, null, "mm", start, time);
            }
            return exact == null ? null : new WeatherValues.Precipitation("exact", exact, null, null,
                    null, null, null, "mm", start, time);
        } catch (NumberFormatException error) { return null; }
    }

    private static Double number(String raw, double min, double max) {
        try {
            if (raw == null) return null;
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) && value >= min && value <= max ? value : null;
        } catch (NumberFormatException error) { return null; }
    }

    private static Map<?, ?> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw WeatherException.unavailable();
        return map;
    }

    private static ZonedDateTime time(String day, String hour, ZonedDateTime publication) {
        LocalDate date = LocalDate.parse(day, DAY);
        int h = Integer.parseInt(hour.substring(0, 2));
        int m = Integer.parseInt(hour.substring(2, 4));
        return (h == 24 ? date.plusDays(1).atStartOfDay() : date.atTime(h, m)).atZone(publication.getZone());
    }
}
