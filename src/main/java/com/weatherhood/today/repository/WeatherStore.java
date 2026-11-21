package com.weatherhood.today.repository;

import com.weatherhood.today.dto.ExactPrecipitation;
import com.weatherhood.today.dto.PollutantReading;
import com.weatherhood.today.dto.PrecipitationRange;
import com.weatherhood.today.dto.SunEvent;
import com.weatherhood.today.dto.WeatherDatum;
import com.weatherhood.today.dto.WeatherField;
import com.weatherhood.today.dto.WeatherSource;
import com.weatherhood.today.entity.WeatherValue;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정규화 자료·수집 시도·일일 호출량의 PostgreSQL 저장소.
 *
 * <p>날씨값은 출처·위치·항목·대상 시각의 유일 키로 갱신한다.
 * 트랜잭션은 DB 연산에만 적용하며 외부 HTTP 호출을 포함하지 않는다.
 */
@Repository
public class WeatherStore {
    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;

    /**
     * 의존 객체와 외부 설정을 받아 WeatherStore를 구성한다.
     *
     * @param entityManager JPA 및 네이티브 SQL 실행기
     * @param jsonMapper    날씨 자료 JSON 변환기
     */
    public WeatherStore(EntityManager entityManager, JsonMapper jsonMapper) {
        this.entityManager = entityManager;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 항목별 최신 유효값을 원자적으로 저장한다.
     *
     * <p>null은 기존 값을 덮지 않는다. 같은 발표 시각이면 더 최근 수집값만 반영한다.
     *
     * @param values    저장할 정규화 자료
     * @param collected 수집 시각
     */
    @Transactional
    public void saveWeatherData(List<WeatherDatum> values, OffsetDateTime collected) {
        for (WeatherDatum datum : values) {
            if (datum.value() == null) {
                continue;
            }
            entityManager.createNativeQuery("""
                            insert into weather_value(source,location,field,target,issued,collected,payload)
                            values (:source,:location,:field,:target,:issued,:collected,:payload)
                            on conflict(source,location,field,target) do update
                            set issued=excluded.issued,collected=excluded.collected,payload=excluded.payload
                            where excluded.issued > weather_value.issued or
                            (excluded.issued = weather_value.issued and excluded.collected >= weather_value.collected)
                            """).setParameter("source", datum.source().name()).setParameter("location", datum.location())
                    .setParameter("field", datum.field().name()).setParameter("target", datum.target())
                    .setParameter("issued", datum.issued())
                    .setParameter("collected", collected)
                    .setParameter("payload", jsonMapper.writeValueAsString(datum.value())).executeUpdate();
        }
    }

    /**
     * 공급 위치별 저장 자료를 조회한다. 이 단계에서는 오래된 자료를 걸러내지 않는다.
     *
     * @param locations 공유 공급 위치 집합
     * @param start     대상 시각 하한(포함)
     * @param end       대상 시각 상한(제외)
     * @return 저장 자료; 유효성은 조합 단계에서 판단
     */
    @Transactional(readOnly = true)
    public List<WeatherDatum> findWeatherData(Set<String> locations, OffsetDateTime start, OffsetDateTime end) {
        if (locations.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "from WeatherValue where location in :locations and target >= :start and target < :end",
                        WeatherValue.class)
                .setParameter("locations", locations).setParameter("start", start).setParameter("end", end)
                .getResultList().stream().map(value -> {
                    WeatherField field = WeatherField.valueOf(value.field());
                    Class<?> type = switch (field) {
                        case SKY, PRECIPITATION_TYPE -> String.class;
                        case PM10, PM25 -> PollutantReading.class;
                        case SUNRISE, SUNSET -> SunEvent.class;
                        case PRECIPITATION_AMOUNT ->
                                jsonMapper.readTree(value.payload()).path("kind").asString().equals("exact")
                                        ? ExactPrecipitation.class : PrecipitationRange.class;
                        default -> Double.class;
                    };
                    return new WeatherDatum(WeatherSource.valueOf(value.source()), value.location(), field,
                            value.target().withOffsetSameInstant(ZoneOffset.ofHours(9)),
                            value.issued().withOffsetSameInstant(ZoneOffset.ofHours(9)),
                            jsonMapper.readValue(value.payload(), type));
                }).toList();
    }

    /**
     * 수집 요청의 다음 시도 가능 시각을 조회한다.
     *
     * @param key 공급 유형·위치·발표 시각을 포함한 수집 키
     * @return 다음 시도 가능 시각, 저장 이력이 없으면 null
     */
    @Transactional(readOnly = true)
    public OffsetDateTime findNextAttemptTime(String key) {
        var rows = entityManager.createNativeQuery("select next_attempt from weather_collection where id=:id",
                OffsetDateTime.class).setParameter("id", key).getResultList();
        return rows.isEmpty() ? null : (OffsetDateTime) rows.getFirst();
    }

    /**
     * 공급처의 한국 날짜별 호출 사용량을 원자적으로 증가시킨다.
     *
     * @param provider 공급처 식별자
     * @param date     호출량 집계 날짜
     * @param limit    양수 일일 호출 상한
     * @return 한도 내에서 예약했으면 true, 이미 한도에 도달했으면 false
     */
    @Transactional
    public boolean tryReserveProviderCall(String provider, LocalDate date, int limit) {
        return !entityManager.createNativeQuery("""
                        insert into weather_request_quota(provider,request_date,calls) values(:provider,:date,1)
                        on conflict(provider,request_date) do update set calls=weather_request_quota.calls+1
                        where weather_request_quota.calls < :limit returning calls
                        """, Integer.class).setParameter("provider", provider).setParameter("date", date)
                .setParameter("limit", limit).getResultList().isEmpty();
    }

    /**
     * 수집 시각·다음 시도 시각과 성공 여부를 저장한다.
     *
     * @param key     공급 유형·위치·발표 시각을 포함한 수집 키
     * @param now     현재 시각
     * @param next    다음 수집 시도가 가능한 시각
     * @param success 이번 수집 성공 여부
     */
    @Transactional
    public void recordCollectionAttempt(String key, OffsetDateTime now, OffsetDateTime next, boolean success) {
        entityManager.createNativeQuery("""
                        insert into weather_collection(id,next_attempt,last_attempt,last_success)
                        values(:id,:next,:now,:success)
                        on conflict(id) do update set next_attempt=excluded.next_attempt,
                        last_attempt=excluded.last_attempt,
                        last_success=coalesce(excluded.last_success,weather_collection.last_success)
                        """).setParameter("id", key).setParameter("next", next).setParameter("now", now)
                .setParameter("success", success ? now : null).executeUpdate();
    }
}
