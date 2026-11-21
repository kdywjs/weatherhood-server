package com.weatherhood.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 암호학적 난수 토큰 생성과 DB 조회용 단방향 해시.
 */
public final class SecretTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretTokens() {
    }

    /**
     * 32바이트 암호학적 난수를 Base64 URL 형식으로 생성한다.
     *
     * @return 43자의 Base64 URL 무작위 토큰
     */
    public static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 토큰 원문을 SHA-256 해시로 변환한다.
     *
     * @param token 로그에 기록하지 않을 토큰 원문
     * @return 64자의 소문자 16진수 해시
     */
    public static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 토큰 해시를 일정 시간 비교 함수로 비교한다.
     *
     * @param token        로그에 기록하지 않을 토큰 원문
     * @param expectedHash 비교할 SHA-256 해시
     * @return 토큰의 해시가 일치하면 true
     */
    public static boolean matchesHash(String token, String expectedHash) {
        return token != null && MessageDigest.isEqual(hashToken(token).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
