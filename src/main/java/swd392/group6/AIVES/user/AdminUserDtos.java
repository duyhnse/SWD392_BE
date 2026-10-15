package swd392.group6.AIVES.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;
import java.util.UUID;

/** Request/response shapes of the admin user endpoints (14_AUTH_AND_ACCOUNTS.md §5). */
final class AdminUserDtos {

    private AdminUserDtos() {
    }

    record CreateUserRequest(
            @NotBlank(message = "Username is required") @Size(max = 50) String username,
            @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Email format is invalid") @Size(max = 100) String email,
            @NotNull(message = "Role is required") Role role,
            @Size(max = 20) String studentCode // required when role = STUDENT (D40)
    ) {
    }

    record UpdateUserRequest(
            @Size(max = 100) String fullName,
            @Email(message = "Email format is invalid") @Size(max = 100) String email,
            @Size(max = 20) String studentCode,
            Boolean active,
            Role role
    ) {
    }

    record CourseRef(UUID courseId, String code, String name, boolean active) {
    }

    /**
     * {@code GET /admin/users/{id}}: the profile plus role details — assigned courses for a lecturer,
     * number of lượt thi for a student (null when not applicable).
     */
    record UserDetail(@JsonUnwrapped UserResponseDTO user, List<CourseRef> courses, Long examSessionCount) {
    }

    record LecturerSummary(UUID userId, String username, String fullName, String email, boolean active,
                           String avatarUrl, long courseCount) {
    }

    record RowError(int row, String column, String code, String message) {
    }

    record ImportReport(int total, int created, int existing, List<RowError> errors) {
    }
}
