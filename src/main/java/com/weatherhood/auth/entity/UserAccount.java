package com.weatherhood.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Apple 식별자와 암호화한 공급처 토큰, 사용자 기본 지역을 관리한다.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String appleSubject;
    @Column(nullable = false, columnDefinition = "text")
    private String refreshTokenCiphertext;
    @Column(nullable = false)
    private Instant appleValidatedAt;
    @Column(length = 128)
    private String defaultRegionId;

    /**
     * JPA가 영속 상태를 복원할 때 사용하는 생성자.
     */
    protected UserAccount() {
    }

    /**
     * 내부 식별자를 반환한다.
     *
     * @return 내부 식별자
     */
    public UUID id() {
        return id;
    }

    /**
     * Apple이 발급한 사용자 식별자 sub를 반환한다.
     *
     * @return Apple이 발급한 사용자 식별자 sub
     */
    public String appleSubject() {
        return appleSubject;
    }

    /**
     * 암호화한 Apple refresh token을 반환한다.
     *
     * @return 암호화한 Apple refresh token
     */
    public String refreshTokenCiphertext() {
        return refreshTokenCiphertext;
    }

    /**
     * Apple 자격을 마지막으로 검증한 시각을 반환한다.
     *
     * @return Apple 자격을 마지막으로 검증한 시각
     */
    public Instant appleValidatedAt() {
        return appleValidatedAt;
    }

    /**
     * null은 현재 위치를 뜻하며 저장 지역과 같은 트랜잭션에서 변경한다.
     *
     * @return 기본 지역 ID, null이면 현재 위치
     */
    public String defaultRegionId() {
        return defaultRegionId;
    }

    /**
     * 최신 Apple refresh token 암호문과 검증 시각을 함께 갱신한다.
     *
     * @param ciphertext  nonce와 인증 태그를 포함한 Base64 암호문
     * @param validatedAt Apple 자격을 마지막으로 검증한 시각
     */
    public void updateAppleCredential(String ciphertext, Instant validatedAt) {
        refreshTokenCiphertext = ciphertext;
        appleValidatedAt = validatedAt;
    }

    /**
     * Apple 자격 확인 성공 시각을 갱신한다.
     *
     * @param validatedAt Apple 자격을 마지막으로 검증한 시각
     */
    public void updateAppleValidationTime(Instant validatedAt) {
        appleValidatedAt = validatedAt;
    }

    /**
     * 기본 지역을 변경하며 null이면 현재 위치로 설정한다.
     *
     * @param regionId 내부 행정동 ID
     */
    public void updateDefaultRegion(String regionId) {
        defaultRegionId = regionId;
    }
}
