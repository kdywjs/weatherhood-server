package com.weatherhood.auth.repository;

import com.weatherhood.auth.entity.UserSession;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 세션 조회·폐기와 만료 자료 정리를 담당한다.
 */
public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    /**
     * 사용자의 모든 기기 세션을 삭제한다.
     *
     * @param userId 인증된 사용자의 내부 UUID
     * @return 삭제한 행 수
     */
    @Modifying
    @Query("delete from UserSession s where s.userId = :userId")
    int deleteByUserId(UUID userId);

    /**
     * 현재 시각에 만료됐거나 그보다 오래된 세션을 정리한다.
     *
     * @param now 현재 시각
     * @return 삭제한 행 수
     */
    @Modifying
    @Query("delete from UserSession s where s.expiresAt <= :now")
    int deleteExpiredSessions(Instant now);
}
