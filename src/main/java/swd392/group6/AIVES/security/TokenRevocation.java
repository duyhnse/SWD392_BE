package swd392.group6.AIVES.security;

import java.time.Instant;

/**
 * Implemented by user principals whose earlier tokens can be revoked (D24):
 * a JWT issued before {@link #tokensValidFrom()} is rejected.
 */
public interface TokenRevocation {

    /** @return null when no token has ever been revoked */
    Instant tokensValidFrom();
}
