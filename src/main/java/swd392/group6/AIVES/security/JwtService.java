package swd392.group6.AIVES.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/** Issues and verifies the stateless JWT (single 24 h token, subject = username — D15, D22). */
@Service
public class JwtService {

    private static final int MIN_KEY_BYTES = 32; // 256 bits

    private final SecretKey signInKey;
    private final long jwtExpiration;

    public JwtService(
            @Value("${application.security.jwt.secret-key}") String secretKey,
            @Value("${application.security.jwt.expiration}") long jwtExpiration
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be a Base64 string of at least 256 bits (generate one with: openssl rand -base64 32)");
        }
        this.signInKey = Keys.hmacShaKeyFor(keyBytes);
        this.jwtExpiration = jwtExpiration;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * @param subject     the login id (username)
     * @param extraClaims non-sensitive claims the frontend may read (userId, role, fullName, ...)
     */
    public String generateToken(String subject, Map<String, Object> extraClaims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtExpiration))
                .signWith(signInKey)
                .compact();
    }

    public long getExpirationTime() {
        return jwtExpiration;
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token) && !isRevoked(token, userDetails);
    }

    /** JWT "iat" has second precision, so compare against the revocation time truncated to seconds. */
    private boolean isRevoked(String token, UserDetails userDetails) {
        if (!(userDetails instanceof TokenRevocation revocable) || revocable.tokensValidFrom() == null) {
            return false;
        }
        Instant issuedAt = extractClaim(token, Claims::getIssuedAt).toInstant();
        return issuedAt.isBefore(revocable.tokensValidFrom().truncatedTo(ChronoUnit.SECONDS));
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signInKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
