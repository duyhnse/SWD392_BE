package swd392.group6.AIVES.user.dto;

import swd392.group6.AIVES.common.Language;

/** {@code PATCH /users/me} — the only field a user may change about themselves. */
public record UpdateProfileRequest(Language preferredLanguage) {
}
