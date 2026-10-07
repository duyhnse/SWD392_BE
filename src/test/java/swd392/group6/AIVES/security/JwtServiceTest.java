package swd392.group6.AIVES.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    // 256-bit Base64 test key
    private static final String SECRET = "dGVzdC1vbmx5LXNlY3JldC1rZXktMzItYnl0ZXMtbG9uZyEh";

    private JwtService jwtService;
    private UserDetails user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 86_400_000L);
        user = User.withUsername("test@example.com").password("x").roles("ADMIN").build();
    }

    @Test
    void generatedTokenIsValidAndCarriesSubject() {
        String token = jwtService.generateToken("test@example.com", Map.of("role", "ADMIN"));

        assertEquals("test@example.com", jwtService.extractUsername(token));
        assertEquals("ADMIN", jwtService.extractClaim(token, claims -> claims.get("role", String.class)));
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void tokenForAnotherUserIsInvalid() {
        String token = jwtService.generateToken("test@example.com", Map.of("role", "ADMIN"));
        UserDetails other = User.withUsername("other@example.com").password("x").roles("STUDENT").build();

        assertFalse(jwtService.isTokenValid(token, other));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(SECRET, -1_000L);
        String token = shortLived.generateToken("test@example.com", Map.of());

        assertThrows(JwtException.class, () -> shortLived.isTokenValid(token, user));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken("test@example.com", Map.of("role", "ADMIN"));

        assertThrows(JwtException.class, () -> jwtService.extractUsername(token + "x"));
    }

    @Test
    void tooShortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtService("c2hvcnQ=", 1000L));
    }
}
