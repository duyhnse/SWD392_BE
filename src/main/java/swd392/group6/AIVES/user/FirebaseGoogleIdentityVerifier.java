package swd392.group6.AIVES.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.common.ApiException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Verifies Firebase Authentication ID tokens obtained in the browser with "Sign in with Google".
 * Checks the RS256 signature against Google's published keys, issuer, audience (= Firebase project id) and expiry,
 * then reads the Google account id from the {@code firebase.identities["google.com"]} claim.
 * No Firebase Admin SDK or service account is needed.
 */
@Slf4j
@Component
class FirebaseGoogleIdentityVerifier implements GoogleIdentityVerifier {

    private static final String JWKS =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    private final JwtDecoder decoder;

    @org.springframework.beans.factory.annotation.Autowired
    FirebaseGoogleIdentityVerifier(@Value("${application.google.firebase-project-id}") String projectId) {
        this(withValidators(NimbusJwtDecoder.withJwkSetUri(JWKS).build(), projectId)); // keys fetched lazily and cached
    }

    /** For tests: any decoder (e.g. one with a local public key) already configured with {@link #withValidators}. */
    FirebaseGoogleIdentityVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    static NimbusJwtDecoder withValidators(NimbusJwtDecoder nimbus, String projectId) {
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(projectId));
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://securetoken.google.com/" + projectId), audience));
        return nimbus;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            log.debug("Rejected Google ID token: {}", e.getMessage());
            throw invalid();
        }
        Map<String, Object> firebase = jwt.getClaim("firebase");
        if (firebase == null || !"google.com".equals(firebase.get("sign_in_provider"))
                || !(firebase.get("identities") instanceof Map<?, ?> identities)
                || !(identities.get("google.com") instanceof List<?> googleIds) || googleIds.isEmpty()) {
            throw invalid();
        }
        String email = jwt.getClaimAsString("email");
        Boolean verified = jwt.getClaimAsBoolean("email_verified");
        return new GoogleIdentity(String.valueOf(googleIds.getFirst()),
                email == null ? null : email.toLowerCase(Locale.ROOT), Boolean.TRUE.equals(verified));
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("GOOGLE_TOKEN_INVALID", "Google sign-in could not be verified");
    }
}
