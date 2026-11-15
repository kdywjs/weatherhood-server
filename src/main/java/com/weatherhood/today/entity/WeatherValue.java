package com.weatherhood.today.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;


/**
 * 출처·공급 위치·항목·대상 시각별 정규화 자료를 저장하는 JPA 엔티티.
 *
 * <p>issued는 발표 시각, collected는 수집 시각이다. JSON payload는 저장용이며 API 응답으로 직접 반환하지 않는다.
 */
@Entity
@Table(name = "weather_value")
public class WeatherValue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false)
    private String source;
    @Column(nullable = false)
    private String location;
    @Column(nullable = false)
    private String field;
    @Column(nullable = false)
    private OffsetDateTime target;
    @Column(nullable = false)
    private OffsetDateTime issued;
    @Column(nullable = false)
    private OffsetDateTime collected;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    public String source() {
        return source;
    }

    public String location() {
        return location;
    }

    public String field() {
        return field;
    }

    public OffsetDateTime target() {
        return target;
    }

    public OffsetDateTime issued() {
        return issued;
    }

    public OffsetDateTime collected() {
        return collected;
    }

    public String payload() {
        return payload;
    }

    protected WeatherValue() {
    }
}
