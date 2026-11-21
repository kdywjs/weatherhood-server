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

    /**
     * JPA가 영속 상태를 복원할 때 사용하는 생성자.
     */
    protected WeatherRegion() {
    }

    /**
     * 행정동과 기상 격자·측정소·선택적 대표 좌표를 연결한다.
     *
     * @param id          내부 행정동 ID
     * @param name        상위 행정구역을 포함한 전체 지역명
     * @param supported   날씨 제공을 지원하는 지역인지 여부
     * @param nx          기상청 격자 X, 미매핑이면 null
     * @param ny          기상청 격자 Y, 미매핑이면 null
     * @param pm10Station PM10 조회 측정소, 미매핑이면 null
     * @param pm25Station PM2.5 조회 측정소, 미매핑이면 null
     */
    public WeatherRegion(String id, String name, boolean supported, Integer nx, Integer ny, String pm10Station,
                         String pm25Station) {
        this(id, name, supported, nx, ny, pm10Station, pm25Station, null, null);
    }

    /**
     * 행정동과 기상 격자·측정소·선택적 대표 좌표를 연결한다.
     *
     * @param id          내부 행정동 ID
     * @param name        상위 행정구역을 포함한 전체 지역명
     * @param supported   날씨 제공을 지원하는 지역인지 여부
     * @param nx          기상청 격자 X, 미매핑이면 null
     * @param ny          기상청 격자 Y, 미매핑이면 null
     * @param pm10Station PM10 조회 측정소, 미매핑이면 null
     * @param pm25Station PM2.5 조회 측정소, 미매핑이면 null
     * @param latitude    WGS84 위도(도)
     * @param longitude   WGS84 경도(도)
     */
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

    /**
     * 내부 식별자를 반환한다.
     *
     * @return 내부 식별자
     */
    public String id() {
        return id;
    }

    /**
     * 지역의 전체 표시 이름을 반환한다.
     *
     * @return 지역의 전체 표시 이름
     */
    public String name() {
        return name;
    }

    /**
     * 이 행정동의 날씨 제공 지원 여부를 반환한다.
     *
     * @return 지원하면 true
     */
    public boolean supported() {
        return supported;
    }

    /**
     * 기상청 격자 X를 반환한다.
     *
     * @return 격자 X, 미매핑이면 null
     */
    public Integer nx() {
        return nx;
    }

    /**
     * 기상청 격자 Y를 반환한다.
     *
     * @return 격자 Y, 미매핑이면 null
     */
    public Integer ny() {
        return ny;
    }

    /**
     * 기상청 격자를 X,Y 문자열로 반환한다.
     *
     * @return X,Y 키, 어느 한 축이라도 없으면 null
     */
    public String grid() {
        return nx == null || ny == null ? null : nx + "," + ny;
    }

    /**
     * PM10 자료를 조회할 측정소 이름을 반환한다.
     *
     * @return 측정소 이름, 미매핑이면 null
     */
    public String pm10Station() {
        return pm10Station;
    }

    /**
     * PM2.5 자료를 조회할 측정소 이름을 반환한다.
     *
     * @return 측정소 이름, 미매핑이면 null
     */
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
