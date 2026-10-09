package swd392.group6.AIVES.user;

import swd392.group6.AIVES.common.Language;

/** {@code PATCH /users/me} — the only field a user may change about themselves. */
record UpdateProfileRequest(Language preferredLanguage) {
}
