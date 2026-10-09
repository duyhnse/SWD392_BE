package swd392.group6.AIVES.security;

import org.springframework.security.core.userdetails.UserDetails;

/**
 * Implemented by the user module (D38): is the login session named in a JWT ("sid") still open for this user?
 * Keeps security independent of the user module, like {@link org.springframework.security.core.userdetails.UserDetailsService}.
 */
public interface SessionValidator {

    boolean isSessionOpen(String sessionId, UserDetails user);
}
