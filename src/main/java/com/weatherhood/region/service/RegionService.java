package com.weatherhood.region.service;

import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import com.weatherhood.region.client.KakaoRegionClient;
import com.weatherhood.region.dto.RegionInfo;
import com.weatherhood.region.dto.RegionSearch;
import com.weatherhood.today.entity.WeatherRegion;
import com.weatherhood.today.repository.WeatherRegionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 로그인 없이 사용할 국내 행정동 검색·ID 조회·GPS 매핑.
 */
@Service
public class RegionService {
    private final WeatherRegionRepository regions;
    private final KakaoRegionClient kakao;

    /**
     * 의존 객체와 외부 설정을 받아 RegionService를 구성한다.
     *
     * @param regions 지역 조회 서비스 또는 저장소
     * @param kakao   좌표에서 행정동 코드를 조회할 클라이언트
     */
    public RegionService(WeatherRegionRepository regions, KakaoRegionClient kakao) {
        this.regions = regions;
        this.kakao = kakao;
    }

    /**
     * 앞뒤 공백만 제거하며 내부 공백과 LIKE 특수 문자를 검색 문자로 유지한다.
     *
     * @param query 앞뒤 공백만 제거할 지역 검색어
     * @param page  0부터 시작하는 페이지 번호
     * @return 20개 이하의 지역과 다음 페이지 여부
     */
    public RegionSearch searchRegions(String query, int page) {
        if (query == null || page < 0 || page > 10000) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        String text = query.strip();
        int length = text.codePointCount(0, text.length());
        if (length < 2 || length > 100) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = regions.searchByName(pattern, text, PageRequest.of(page, 20));
        return new RegionSearch(result.getContent().stream().map(RegionInfo::from).toList(), page, result.hasNext());
    }

    /**
     * 지원하는 행정동의 내부 ID와 전체 이름을 조회한다.
     *
     * @param regionId 내부 행정동 ID
     * @return 행정동 내부 ID와 전체 이름
     */
    public RegionInfo getRegion(String regionId) {
        return RegionInfo.from(findSupportedRegion(regionId));
    }

    /**
     * GPS는 외부 행정동 식별에만 사용하며 저장·날씨 격자 계산에 직접 사용하지 않는다.
     *
     * @param latitude  WGS84 위도(도)
     * @param longitude WGS84 경도(도)
     * @return 행정동 내부 ID와 전체 이름
     */
    public RegionInfo resolveCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        String code = kakao.fetchAdministrativeCode(latitude, longitude);
        var region = regions.findByAdminCode(code)
                .orElseThrow(() -> new ApplicationException(ErrorCode.REGION_MAPPING_UNAVAILABLE));
        validateSupportedRegion(region);
        return RegionInfo.from(region);
    }

    /**
     * 내부 ID로 지역을 조회하고 제공 지원 여부를 검증한다.
     *
     * @param regionId 내부 행정동 ID
     * @return 지원 여부를 검증한 행정동
     */
    public WeatherRegion findSupportedRegion(String regionId) {
        var region = regions.findById(regionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.REGION_NOT_FOUND));
        validateSupportedRegion(region);
        return region;
    }

    private void validateSupportedRegion(WeatherRegion region) {
        if (!region.supported()) {
            throw new ApplicationException(ErrorCode.UNSUPPORTED_REGION);
        }
    }
}
