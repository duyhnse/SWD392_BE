package swd392.group6.AIVES.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "Username is required") @Size(max = 50) String username,
        @NotBlank(message = "Email is required") @Size(max = 100) String email
) {
}
