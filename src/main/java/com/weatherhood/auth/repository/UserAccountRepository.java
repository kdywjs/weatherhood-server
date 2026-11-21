package com.weatherhood.auth.repository;

import com.weatherhood.auth.entity.UserAccount;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 계정의 생성 경합과 사용자별 변경을 DB 유일 제약·행 잠금으로 제어한다.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    /**
     * 내부 사용자 ID에 해당하는 행을 변경 잠금으로 조회한다.
     *
     * @param id 내부 사용자 UUID
     * @return 잠근 사용자 또는 빈 Optional
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findLockedById(UUID id);

    /**
     * Apple sub에 해당하는 사용자 행을 변경 잠금으로 조회한다.
     *
     * @param subject Apple 사용자 식별자 sub
     * @return 잠근 사용자 또는 빈 Optional
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.appleSubject = :subject")
    Optional<UserAccount> findLockedByAppleSubject(String subject);

    /**
     * Apple sub가 없을 때만 계정을 삽입해 동시 가입 중복을 방지한다.
     *
     * @param id          내부 사용자 UUID
     * @param subject     Apple 사용자 식별자 sub
     * @param ciphertext  nonce와 인증 태그를 포함한 Base64 암호문
     * @param validatedAt Apple 자격을 마지막으로 검증한 시각
     * @return 새로 생성했으면 1, 이미 있으면 0
     */
    @Modifying
    @Query(value = """
            insert into user_account(id, apple_subject, refresh_token_ciphertext, apple_validated_at)
            values (:id, :subject, :ciphertext, :validatedAt)
            on conflict (apple_subject) do nothing
            """, nativeQuery = true)
    int insertAccountIfAbsent(UUID id, String subject, String ciphertext, Instant validatedAt);
}
