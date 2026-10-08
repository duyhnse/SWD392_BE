package swd392.group6.AIVES.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirm(
        @NotBlank(message = "Token is required") @Size(max = 200) String token,
        @NotBlank(message = "New password is required")
        @Size(min = PasswordRules.MIN_PASSWORD, max = PasswordRules.MAX_PASSWORD,
              message = "Password must be between 8 and 100 characters") String newPassword
) {
}
