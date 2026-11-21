package com.weatherhood.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 서버 Bearer 토큰의 SHA-256과 절대 만료 시각만 보관한다.
 */
@Entity
@Table(name = "user_session")
public class UserSession {
    @Id
    @Column(length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * JPA가 영속 상태를 복원할 때 사용하는 생성자.
     */
    protected UserSession() {
    }

    /**
     * 세션 원문 대신 해시와 소유자·만료 시각을 보관한다.
     *
     * @param tokenHash 원문 세션 토큰의 SHA-256 해시
     * @param userId    인증된 사용자의 내부 UUID
     * @param expiresAt UTC 기준 절대 만료 시각
     */
    public UserSession(String tokenHash, UUID userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    /**
     * 세션 소유자의 내부 UUID를 반환한다.
     *
     * @return 세션 소유자의 내부 UUID
     */
    public UUID userId() {
        return userId;
    }

    /**
     * UTC 기준 절대 만료 시각을 반환한다.
     *
     * @return UTC 기준 절대 만료 시각
     */
    public Instant expiresAt() {
        return expiresAt;
    }
}
