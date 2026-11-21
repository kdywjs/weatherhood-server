package com.weatherhood.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.weatherhood.auth.client.AppleIdentityVerifier;
import com.weatherhood.auth.service.TokenCipher;
import com.weatherhood.common.exception.ApplicationException;
import com.weatherhood.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AppleSecurityTest {
    private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");
    private static IdentityProviderStub provider;
    private static AppleIdentityVerifier verifier;

    @BeforeAll
    static void initializeVerifier() throws Exception {
        provider = new IdentityProviderStub();
        verifier = new AppleIdentityVerifier(provider.baseUrl() + "/auth/keys", IdentityProviderStub.CLIENT_ID,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterAll
    static void closeProvider() throws Exception {
        provider.close();
    }

    @Test
    void signedIdentityRequiresIssuerAudienceSubjectAndDates() throws Exception {
        assertThat(verifier.verifyIdentity(provider.signClaims(validClaims().build())).getSubject()).isEqualTo("user");
        assertInvalid(provider.signClaims(validClaims().issuer("https://attacker.example").build()));
        assertInvalid(provider.signClaims(validClaims().audience("another-app").build()));
        assertInvalid(provider.signClaims(validClaims().audience((String) null).build()));
        assertInvalid(provider.signClaims(validClaims().expirationTime(Date.from(NOW)).build()));
        assertInvalid(provider.signClaims(validClaims().expirationTime(null).build()));
        assertInvalid(provider.signClaims(validClaims().issueTime(null).build()));
        assertInvalid(provider.signClaims(validClaims().issueTime(Date.from(NOW.plusSeconds(120))).build()));
        assertInvalid(provider.signClaims(validClaims().subject("").build()));
        assertInvalid(provider.signClaims(validClaims().subject(null).build()));
    }

    @Test
    void forgedSignatureAndUnsignedTokensAreRejected() throws Exception {
        var forged = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(provider.signingKey.getKeyID()).build(), validClaims().build());
        forged.sign(new RSASSASigner(new RSAKeyGenerator(2048).generate()));
        assertInvalid(forged.serialize());
        assertInvalid("eyJhbGciOiJub25lIn0.e30.");
        assertInvalid("not-a-jwt");
    }

    @Test
    void unavailablePublicKeyEndpointIsNotTreatedAsValidCredentials() {
        var unavailable = new AppleIdentityVerifier(provider.baseUrl() + "/missing-keys",
                IdentityProviderStub.CLIENT_ID,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> unavailable.verifyIdentity(provider.issueIdentity("user", "nonce")))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).code())
                        .isEqualTo(ErrorCode.AUTH_UNAVAILABLE));
    }

    @Test
    void encryptedTokensUseDistinctNoncesAndRejectTamperingOrWrongKeys() {
        var cipher = new TokenCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        String first = cipher.encryptToken("refresh-secret");
        String second = cipher.encryptToken("refresh-secret");
        assertThat(first).isNotEqualTo(second).doesNotContain("refresh-secret");
        assertThat(cipher.decryptToken(first)).isEqualTo("refresh-secret");
        byte[] modified = Base64.getDecoder().decode(first);
        modified[modified.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decryptToken(Base64.getEncoder().encodeToString(modified)))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> new TokenCipher("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=").decryptToken(first))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> new TokenCipher("").encryptToken("refresh-secret"))
                .isInstanceOf(ApplicationException.class);
    }

    private JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder().issuer("https://appleid.apple.com").audience(IdentityProviderStub.CLIENT_ID)
                .subject("user").issueTime(Date.from(NOW.minusSeconds(1)))
                .expirationTime(Date.from(NOW.plusSeconds(600))).claim("nonce", "nonce");
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> verifier.verifyIdentity(token)).isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).code())
                        .isEqualTo(ErrorCode.INVALID_APPLE_CREDENTIAL));
    }
}
