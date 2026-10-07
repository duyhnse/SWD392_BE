package swd392.group6.AIVES.user;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared normalisation and validation rules for account fields (14_AUTH_AND_ACCOUNTS.md §2, BR-A4). */
final class PasswordRules {

    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 100;
    static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");

    private PasswordRules() {
    }

    static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
