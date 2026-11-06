package swd392.group6.AIVES.user.dto;

import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.service.PasswordRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DevRegistrationRequest(
        @NotBlank(message = "Username is required") @Size(max = 50) String username,
        @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Email format is invalid") @Size(max = 100) String email,
        @Size(max = 20) String studentCode, // required for STUDENT (checked by provisioning, D40)
        @NotNull(message = "Role is required") Role role,
        @NotBlank(message = "Password is required")
        @Size(min = PasswordRules.MIN_PASSWORD, max = PasswordRules.MAX_PASSWORD,
              message = "Password must be between 8 and 100 characters") String password,
        @NotBlank(message = "Please repeat the password") String confirmPassword
) {
}
