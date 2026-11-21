package com.weatherhood.auth.service;

import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apple refresh token만 AES-256-GCM으로 보호한다. 키는 외부 환경변수에서 공급한다.
 */
@Component
public class TokenCipher {
    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();

    /**
     * 의존 객체와 외부 설정을 받아 TokenCipher를 구성한다.
     *
     * @param encodedKey Base64로 인코딩한 AES-256 키
     */
    public TokenCipher(@Value("${auth.token-encryption-key:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    /**
     * 무작위 nonce를 사용해 Apple refresh token을 AES-GCM으로 암호화한다.
     *
     * @param token 로그에 기록하지 않을 토큰 원문
     * @return 변환한 토큰 문자열
     */
    public String encryptToken(String token) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, iv);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
        }
    }

    /**
     * 인증 태그를 검증한 뒤 Apple refresh token 원문을 복원한다.
     *
     * @param ciphertext nonce와 인증 태그를 포함한 Base64 암호문
     * @return 변환한 토큰 문자열
     */
    public String decryptToken(String ciphertext) {
        try {
            byte[] bytes = Base64.getDecoder().decode(ciphertext);
            if (bytes.length < 28) {
                throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
            }
            byte[] iv = new byte[12];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return new String(createCipher(Cipher.DECRYPT_MODE, iv).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
        }
    }

    private Cipher createCipher(int mode, byte[] iv) throws GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) {
            throw new ApplicationException(ErrorCode.AUTH_UNAVAILABLE);
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return cipher;
    }
}
