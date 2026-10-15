package swd392.group6.AIVES.security;

/** Implemented by user principals that can be required to change their password first (D62). */
public interface PasswordChangeRequirement {

    boolean isMustChangePassword();
}
