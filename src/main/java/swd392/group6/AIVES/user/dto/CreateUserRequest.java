package swd392.group6.AIVES.user.dto;

import swd392.group6.AIVES.user.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Username is required") @Size(max = 50) String username,
        @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Email format is invalid") @Size(max = 100) String email,
        @NotNull(message = "Role is required") Role role,
        @Size(max = 20) String studentCode, // required when role = STUDENT (D40)
        @Size(max = 100) String password // blank = account.default_password; changed at the first login (D62)
) {
}
