package swd392.group6.AIVES.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import swd392.group6.AIVES.model.Role;
import swd392.group6.AIVES.model.User;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    // 256-bit Base64 test key
    private static final String SECRET = "dGVzdC1vbmx5LXNlY3JldC1rZXktMzItYnl0ZXMtbG9uZyEh";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 86_400_000L);
        user = User.builder()
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .roleId(Role.ADMIN.getId())
                .hashedPassword("someHashedPassword")
                .build();
    }

    @Test
    void generatedTokenIsValidAndCarriesSubject() {
        String token = jwtService.generateToken(user);

        assertEquals("test@example.com", jwtService.extractUsername(token));
        assertEquals("ADMIN", jwtService.extractClaim(token, claims -> claims.get("role", String.class)));
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void tokenForAnotherUserIsInvalid() {
        String token = jwtService.generateToken(user);
        User other = User.builder().email("other@example.com").hashedPassword("x").build();

        assertFalse(jwtService.isTokenValid(token, other));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(SECRET, -1_000L);
        String token = shortLived.generateToken(user);

        assertThrows(JwtException.class, () -> shortLived.isTokenValid(token, user));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(user);

        assertThrows(JwtException.class, () -> jwtService.extractUsername(token + "x"));
    }

    @Test
    void tooShortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtService("c2hvcnQ=", 1000L));
    }
}
