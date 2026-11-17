package com.weatherhood.today.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 내부 지역 ID에 행정동 정보와 날씨 공급 위치를 연결하는 JPA 엔티티.
 *
 * <p>여러 지역이 기상 격자나 측정소를 공유할 수 있다. 현재 위치의 GPS 자체를 저장하는 테이블이 아니다.
 */
@Entity
@Table(name = "weather_region")
public class WeatherRegion {
    @Id
    @Column(length = 128)
    private String id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private boolean supported;
    @Column(unique = true, length = 10)
    private String adminCode;
    private Double latitude;
    private Double longitude;
    private Double stationDistanceKm;
    private Integer nx;
    private Integer ny;
    private String pm10Station;
    private String pm25Station;

    protected WeatherRegion() {
    }

    public WeatherRegion(String id, String name, boolean supported, Integer nx, Integer ny, String pm10Station,
                         String pm25Station) {
        this(id, name, supported, nx, ny, pm10Station, pm25Station, null, null);
    }

    public WeatherRegion(String id, String name, boolean supported, Integer nx, Integer ny, String pm10Station,
                         String pm25Station, Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.id = id;
        this.name = name;
        this.supported = supported;
        this.nx = nx;
        this.ny = ny;
        this.pm10Station = pm10Station;
        this.pm25Station = pm25Station;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean supported() {
        return supported;
    }

    public Integer nx() {
        return nx;
    }

    public Integer ny() {
        return ny;
    }

    public String grid() {
        return nx == null || ny == null ? null : nx + "," + ny;
    }

    public String pm10Station() {
        return pm10Station;
    }

    public String pm25Station() {
        return pm25Station;
    }

    /**
     * 출몰시각 조회와 저장을 공유할 대표 좌표 키. 실제 사용자 GPS는 포함하지 않는다.
     *
     * @return 위도,경도 순서의 좌표 키, 좌표가 없거나 유효하지 않으면 null
     */
    public String sunLocation() {
        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return null;
        }
        return latitude + "," + longitude;
    }
}
