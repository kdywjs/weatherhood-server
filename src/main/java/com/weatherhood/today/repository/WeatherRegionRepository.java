package com.weatherhood.today.repository;

import com.weatherhood.today.entity.WeatherRegion;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 내부 지역 ID로 행정동과 공급 위치 연결 정보를 조회하는 저장소.
 */
public interface WeatherRegionRepository extends JpaRepository<WeatherRegion, String> {
}
