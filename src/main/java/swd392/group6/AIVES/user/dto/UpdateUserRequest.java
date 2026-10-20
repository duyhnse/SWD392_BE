package swd392.group6.AIVES.user.dto;

import swd392.group6.AIVES.user.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 100) String fullName,
        @Email(message = "Email format is invalid") @Size(max = 100) String email,
        @Size(max = 20) String studentCode,
        Boolean active,
        Role role
) {
}
