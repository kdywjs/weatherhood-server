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

    /**
     * 저장 자료의 공급 유형을 반환한다.
     *
     * @return 저장 자료의 공급 유형
     */
    public String source() {
        return source;
    }

    /**
     * 공급 자료를 공유하는 위치 키를 반환한다.
     *
     * @return 공급 자료를 공유하는 위치 키
     */
    public String location() {
        return location;
    }

    /**
     * 저장한 날씨 항목 식별자를 반환한다.
     *
     * @return 저장한 날씨 항목 식별자
     */
    public String field() {
        return field;
    }

    /**
     * 자료가 나타내는 대상 시각을 반환한다.
     *
     * @return 자료가 나타내는 대상 시각
     */
    public OffsetDateTime target() {
        return target;
    }

    /**
     * 자료의 발표 시각을 반환한다.
     *
     * @return 자료의 발표 시각
     */
    public OffsetDateTime issued() {
        return issued;
    }

    /**
     * 공급 자료를 서버에 저장한 시각을 반환한다.
     *
     * @return 공급 자료를 서버에 저장한 시각
     */
    public OffsetDateTime collected() {
        return collected;
    }

    /**
     * 정규화한 날씨값의 JSON을 반환한다.
     *
     * @return 정규화한 날씨값의 JSON
     */
    public String payload() {
        return payload;
    }

    /**
     * JPA가 영속 상태를 복원할 때 사용하는 생성자.
     */
    protected WeatherValue() {
    }
}
