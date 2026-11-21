package com.weatherhood.region.service;

import com.weatherhood.auth.entity.UserAccount;
import com.weatherhood.auth.repository.UserAccountRepository;
import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import com.weatherhood.region.dto.RegionInfo;
import com.weatherhood.region.dto.SavedLocations;
import com.weatherhood.region.entity.SavedRegion;
import com.weatherhood.region.repository.SavedRegionRepository;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 행 잠금 아래에서 최대 개수·중복·정렬·기본 지역의 일관성을 유지한다.
 */
@Service
@Transactional
public class SavedRegionService {
    private final UserAccountRepository users;
    private final SavedRegionRepository saved;
    private final RegionService regions;

    /**
     * 의존 객체와 외부 설정을 받아 SavedRegionService를 구성한다.
     *
     * @param users   내부 사용자 계정 저장소
     * @param saved   사용자별 저장 지역 저장소
     * @param regions 지역 조회 서비스 또는 저장소
     */
    public SavedRegionService(UserAccountRepository users, SavedRegionRepository saved, RegionService regions) {
        this.users = users;
        this.saved = saved;
        this.regions = regions;
    }

    /**
     * 사용자별 저장 순서와 실행 기본 지역을 함께 조회한다.
     *
     * @param userId 인증된 사용자의 내부 UUID
     * @return 현재 저장 순서와 실행 기본 지역
     */
    public SavedLocations getSavedLocations(UUID userId) {
        var user = lockUser(userId);
        return composeLocations(user, saved.findOrderedByUserId(userId));
    }

    /**
     * 지역을 목록 끝에 추가하며 중복은 유지하고 최대 10개를 허용한다.
     *
     * @param userId   인증된 사용자의 내부 UUID
     * @param regionId 내부 행정동 ID
     * @return 현재 저장 순서와 실행 기본 지역
     */
    public SavedLocations addSavedRegion(UUID userId, String regionId) {
        var user = lockUser(userId);
        var items = saved.findOrderedByUserId(userId);
        if (items.stream().anyMatch(item -> item.region().id().equals(regionId))) {
            return composeLocations(user, items);
        }
        var region = regions.findSupportedRegion(regionId);
        if (items.size() >= 10) {
            throw new ApplicationException(ErrorCode.SAVED_REGION_LIMIT);
        }
        items.add(saved.save(new SavedRegion(userId, region, items.size())));
        return composeLocations(user, items);
    }

    /**
     * 저장 항목을 제거하고 삭제한 기본 지역은 현재 위치로 되돌린다.
     *
     * @param userId   인증된 사용자의 내부 UUID
     * @param regionId 내부 행정동 ID
     * @return 현재 저장 순서와 실행 기본 지역
     */
    public SavedLocations deleteSavedRegion(UUID userId, String regionId) {
        var user = lockUser(userId);
        var items = saved.findOrderedByUserId(userId);
        var removed = items.stream().filter(item -> item.region().id().equals(regionId)).toList();
        saved.deleteAll(removed);
        items.removeAll(removed);
        if (regionId.equals(user.defaultRegionId())) {
            user.updateDefaultRegion(null);
        }
        for (int index = 0; index < items.size(); index++) {
            items.get(index).updatePosition(index);
        }
        return composeLocations(user, items);
    }

    /**
     * 전체 집합이 일치하지 않으면 최신 목록 재조회가 필요한 409를 반환한다.
     *
     * @param userId    인증된 사용자의 내부 UUID
     * @param regionIds 원하는 순서의 전체 저장 지역 ID 목록
     * @return 현재 저장 순서와 실행 기본 지역
     */
    public SavedLocations reorderSavedRegions(UUID userId, List<String> regionIds) {
        var user = lockUser(userId);
        var items = saved.findOrderedByUserId(userId);
        var existingIds = items.stream().map(item -> item.region().id()).toList();
        if (regionIds.size() != existingIds.size() || new HashSet<>(regionIds).size() != regionIds.size()
                || !new HashSet<>(regionIds).equals(new HashSet<>(existingIds))) {
            throw new ApplicationException(ErrorCode.REGION_ORDER_CONFLICT);
        }
        for (var item : items) {
            item.updatePosition(regionIds.indexOf(item.region().id()));
        }
        var ordered = regionIds.stream().map(id -> items.stream()
                .filter(item -> item.region().id().equals(id)).findFirst().orElseThrow()).toList();
        return composeLocations(user, ordered);
    }

    /**
     * 현재 위치 또는 이미 저장한 지역을 실행 기본값으로 지정한다.
     *
     * @param userId   인증된 사용자의 내부 UUID
     * @param type     current_location 또는 saved_region
     * @param regionId 내부 행정동 ID
     * @return 현재 저장 순서와 실행 기본 지역
     */
    public SavedLocations updateDefaultLocation(UUID userId, String type, String regionId) {
        var user = lockUser(userId);
        var items = saved.findOrderedByUserId(userId);
        if ("current_location".equals(type) && regionId == null) {
            user.updateDefaultRegion(null);
        } else if ("saved_region".equals(type) && regionId != null && !regionId.isBlank()) {
            if (items.stream().noneMatch(item -> item.region().id().equals(regionId))) {
                throw new ApplicationException(ErrorCode.DEFAULT_REGION_NOT_SAVED);
            }
            user.updateDefaultRegion(regionId);
        } else {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        return composeLocations(user, items);
    }

    private UserAccount lockUser(UUID userId) {
        return users.findLockedById(userId).orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private SavedLocations composeLocations(UserAccount user, List<SavedRegion> items) {
        var regions = items.stream().map(item -> RegionInfo.from(item.region())).toList();
        var defaultRegion = regions.stream().filter(region -> region.id().equals(user.defaultRegionId()))
                .findFirst().orElse(null);
        return new SavedLocations(regions, defaultRegion);
    }
}
