package com.weatherhood.weather.infrastructure;

import com.weatherhood.weather.domain.WeatherException;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class KmaRequestBudget {
    public static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final int dailyCalls;
    private final long dailyBytes;

    public KmaRequestBudget(JdbcTemplate jdbc,
                            @Value("${weather.kma.daily-call-budget:19000}") int dailyCalls,
                            @Value("${weather.kma.daily-byte-budget:4500000000}") long dailyBytes) {
        this.jdbc = jdbc;
        this.dailyCalls = dailyCalls;
        this.dailyBytes = dailyBytes;
    }

    public void reserve(LocalDate day) {
        try {
            jdbc.update("INSERT INTO kma_usage (usage_day,calls,reserved_bytes) VALUES (?,0,0)", day);
        } catch (DuplicateKeyException alreadyExists) { /* Existing day's counters must not be reset. */ }
        int changed = jdbc.update("UPDATE kma_usage SET calls=calls+1,reserved_bytes=reserved_bytes+? "
                        + "WHERE usage_day=? AND calls<? AND reserved_bytes<=?",
                MAX_RESPONSE_BYTES, day, dailyCalls, dailyBytes - MAX_RESPONSE_BYTES);
        if (changed != 1) throw WeatherException.unavailable();
    }

    public void complete(LocalDate day, int receivedBytes) {
        jdbc.update("UPDATE kma_usage SET reserved_bytes=reserved_bytes-? WHERE usage_day=?",
                Math.max(0, MAX_RESPONSE_BYTES - receivedBytes), day);
    }
}
