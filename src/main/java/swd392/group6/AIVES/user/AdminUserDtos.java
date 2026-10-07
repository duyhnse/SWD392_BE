package swd392.group6.AIVES.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request/response shapes of the admin user endpoints (14_AUTH_AND_ACCOUNTS.md §5). */
final class AdminUserDtos {

    private AdminUserDtos() {
    }

    record CreateUserRequest(
            @NotBlank(message = "Username is required") @Size(max = 50) String username,
            @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Email format is invalid") @Size(max = 100) String email,
            @NotNull(message = "Role is required") Role role,
            @Size(max = 20) String studentCode
    ) {
    }

    record UpdateUserRequest(
            @Size(max = 100) String fullName,
            @Email(message = "Email format is invalid") @Size(max = 100) String email,
            @Size(max = 20) String studentCode,
            Boolean active
    ) {
    }

    record RowError(int row, String column, String code, String message) {
    }

    record ImportReport(int total, int created, int existing, List<RowError> errors) {
    }
}
