package swd392.group6.AIVES.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Firebase ID token from "Sign in with Google" in the browser. */
public record GoogleTokenRequest(@NotBlank(message = "idToken is required") @Size(max = 8192) String idToken,
                                 @Size(max = 64) String deviceId, Boolean force) {
}
