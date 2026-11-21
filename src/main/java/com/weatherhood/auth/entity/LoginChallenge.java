package com.weatherhood.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Apple 로그인 요청에 연결할 일회용 nonce 해시와 5분 만료 시각.
 */
@Entity
@Table(name = "login_challenge")
public class LoginChallenge {
    @Id
    private UUID id;
    @Column(nullable = false, length = 64)
    private String nonceHash;
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * JPA가 영속 상태를 복원할 때 사용하는 생성자.
     */
    protected LoginChallenge() {
    }

    /**
     * nonce 원문 대신 해시와 절대 만료 시각을 보관한다.
     *
     * @param id        challenge 식별 UUID
     * @param nonceHash 로그인 nonce의 SHA-256 해시
     * @param expiresAt UTC 기준 절대 만료 시각
     */
    public LoginChallenge(UUID id, String nonceHash, Instant expiresAt) {
        this.id = id;
        this.nonceHash = nonceHash;
        this.expiresAt = expiresAt;
    }

    /**
     * 검증에 사용할 nonce의 SHA-256 해시를 반환한다.
     *
     * @return 검증에 사용할 nonce의 SHA-256 해시
     */
    public String nonceHash() {
        return nonceHash;
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
