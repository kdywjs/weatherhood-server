package com.weatherhood.region.repository;

import com.weatherhood.region.entity.SavedRegion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 사용자 조건을 필수로 사용해 저장 목록을 지역명과 함께 읽는다.
 */
public interface SavedRegionRepository extends JpaRepository<SavedRegion, UUID> {
    /**
     * 지정한 사용자의 저장 지역을 이름과 함께 순서대로 조회한다.
     *
     * @param userId 인증된 사용자의 내부 UUID
     * @return 사용자별 저장 순서의 지역 목록
     */
    @Query("select s from SavedRegion s join fetch s.region where s.userId = :userId order by s.position, s.id")
    List<SavedRegion> findOrderedByUserId(UUID userId);
}
