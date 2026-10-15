package swd392.group6.AIVES.user;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import swd392.group6.AIVES.common.ApiException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Firebase ID token checks with a locally generated RSA key standing in for Google's keys. */
class FirebaseGoogleIdentityVerifierTest {

    private static final String PROJECT = "aives-55658";
    private static final KeyPair KEYS = rsa();
    private final FirebaseGoogleIdentityVerifier verifier = new FirebaseGoogleIdentityVerifier(
            FirebaseGoogleIdentityVerifier.withValidators(
                    NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build(), PROJECT));

    @Test
    void validGoogleTokenYieldsTheGoogleAccountId() {
        var identity = verifier.verify(token(PROJECT, "https://securetoken.google.com/" + PROJECT, "google.com", 3600));

        assertThat(identity.subject()).isEqualTo("109876543210");
        assertThat(identity.email()).isEqualTo("vinh@gmail.com");
        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    void tokenOfAnotherProjectIsRejected() {
        assertInvalid(token("other-project", "https://securetoken.google.com/other-project", "google.com", 3600));
        assertInvalid(token(PROJECT, "https://securetoken.google.com/other-project", "google.com", 3600));
    }

    @Test
    void expiredTokenIsRejected() {
        assertInvalid(token(PROJECT, "https://securetoken.google.com/" + PROJECT, "google.com", -600));
    }

    @Test
    void nonGoogleSignInIsRejected() {
        assertInvalid(token(PROJECT, "https://securetoken.google.com/" + PROJECT, "password", 3600));
    }

    @Test
    void garbageIsRejected() {
        assertInvalid("not.a.token");
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("GOOGLE_TOKEN_INVALID");
    }

    private static String token(String audience, String issuer, String provider, long expiresInSec) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer).audience(audience).subject("firebase-uid-1")
                    .issueTime(Date.from(now.minusSeconds(60))).expirationTime(Date.from(now.plusSeconds(expiresInSec)))
                    .claim("email", "Vinh@Gmail.com").claim("email_verified", true)
                    .claim("firebase", Map.of("sign_in_provider", provider,
                            "identities", Map.of("google.com", List.of("109876543210"), "email", List.of("vinh@gmail.com"))))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
