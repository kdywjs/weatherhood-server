package com.weatherhood.today.repository;

import com.weatherhood.today.entity.WeatherRegion;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 내부 지역 ID로 행정동과 공급 위치 연결 정보를 조회하는 저장소.
 */
public interface WeatherRegionRepository extends JpaRepository<WeatherRegion, String> {
    /**
     * 행정동 코드에 연결된 내부 지역을 조회한다.
     *
     * @param adminCode 10자리 행정동 코드
     * @return 연결된 지역 또는 빈 Optional
     */
    Optional<WeatherRegion> findByAdminCode(String adminCode);

    /**
     * 리터럴 포함 검색과 정확 이름 우선 정렬로 지원 지역을 조회한다.
     *
     * @param pattern   느낌표로 LIKE 특수 문자를 이스케이프한 포함 검색 패턴
     * @param exactName 정확 일치 우선 정렬에 사용할 원문 이름
     * @param pageable  페이지와 조회 개수
     * @return 정렬한 지역과 다음 페이지 존재 여부
     */
    @Query("""
            select r from WeatherRegion r where r.supported = true and r.name like :pattern escape '!'
            order by case when r.name = :exactName then 0 else 1 end, r.name, r.id
            """)
    Slice<WeatherRegion> searchByName(String pattern, String exactName, Pageable pageable);
}
