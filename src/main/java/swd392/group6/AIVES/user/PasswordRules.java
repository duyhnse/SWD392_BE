package swd392.group6.AIVES.user;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared normalisation and validation rules for account fields (14_AUTH_AND_ACCOUNTS.md §2, BR-A4). */
final class PasswordRules {

    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 100;
    static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");
    /** Student code (MSSV), stored uppercase, e.g. SE180180. Required for STUDENT accounts (D40). */
    static final Pattern STUDENT_CODE = Pattern.compile("[A-Z0-9]{2,20}");

    /** Uppercased student code, or null when blank. */
    static String normalizeStudentCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private PasswordRules() {
    }

    static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
