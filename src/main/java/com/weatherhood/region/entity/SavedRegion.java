package com.weatherhood.region.entity;

import com.weatherhood.today.entity.WeatherRegion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 사용자별 지역과 정렬 위치. 현재 위치는 저장 행으로 만들지 않는다.
 */
@Entity
@Table(name = "saved_region")
public class SavedRegion {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private WeatherRegion region;
    @Column(nullable = false)
    private int position;

    /**
     * JPA가 영속 상태를 복원할 때 사용하는 생성자.
     */
    protected SavedRegion() {
    }

    /**
     * 사용자에게 연결된 지역과 저장 순서를 생성한다.
     *
     * @param userId   인증된 사용자의 내부 UUID
     * @param region   행정동 식별 정보
     * @param position 0부터 시작하는 저장 목록 순서
     */
    public SavedRegion(UUID userId, WeatherRegion region, int position) {
        id = UUID.randomUUID();
        this.userId = userId;
        this.region = region;
        this.position = position;
    }

    /**
     * 저장 항목에 연결된 행정동을 반환한다.
     *
     * @return 저장 항목에 연결된 행정동
     */
    public WeatherRegion region() {
        return region;
    }

    /**
     * 사용자 행 잠금 아래에서 저장 목록의 순서를 변경한다.
     *
     * @param position 0부터 시작하는 저장 목록 순서
     */
    public void updatePosition(int position) {
        this.position = position;
    }
}
