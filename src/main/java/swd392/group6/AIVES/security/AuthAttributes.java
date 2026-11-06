package swd392.group6.AIVES.security;

/** Request attributes set by {@link JwtAuthenticationFilter}. */
public final class AuthAttributes {

    /** Id of the login session of the current request (JWT "sid"). */
    public static final String SESSION_ID = "aives.auth.sessionId";
    /** Why a bearer token was refused, used as the 401 problem code (e.g. SESSION_REVOKED). */
    public static final String ERROR_CODE = "aives.auth.errorCode";

    private AuthAttributes() {
    }
}
