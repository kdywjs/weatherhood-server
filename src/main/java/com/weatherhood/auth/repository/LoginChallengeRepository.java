package com.weatherhood.auth.repository;

import com.weatherhood.auth.entity.LoginChallenge;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 조건부 삭제 결과로 로그인 challenge의 일회성 사용을 보장한다.
 */
public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, UUID> {
    /**
     * 유효한 challenge를 조건부 삭제하여 한 요청만 소비하도록 한다.
     *
     * @param id  서버가 발급한 challenge UUID
     * @param now 현재 시각
     * @return 소비했으면 1, 없거나 만료됐으면 0
     */
    @Modifying
    @Query("delete from LoginChallenge c where c.id = :id and c.expiresAt > :now")
    int consumeChallenge(UUID id, Instant now);

    /**
     * 현재 시각에 만료됐거나 그보다 오래된 challenge를 정리한다.
     *
     * @param now 현재 시각
     * @return 삭제한 행 수
     */
    @Modifying
    @Query("delete from LoginChallenge c where c.expiresAt <= :now")
    int deleteExpiredChallenges(Instant now);
}
