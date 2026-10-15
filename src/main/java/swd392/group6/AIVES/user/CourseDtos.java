package swd392.group6.AIVES.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import swd392.group6.AIVES.common.Language;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response shapes of the course endpoints (15_CRUD_CATALOGUE.md §5.1). */
final class CourseDtos {

    private CourseDtos() {
    }

    record CreateCourseRequest(
            @NotBlank(message = "Code is required") @Size(max = 20) String code,
            @NotBlank(message = "Name is required") @Size(max = 150) String name,
            String description,
            Language defaultLanguage
    ) {
    }

    record UpdateCourseRequest(
            @Size(max = 20) String code,
            @Size(max = 150) String name,
            String description,
            Language defaultLanguage,
            Boolean active
    ) {
    }

    record AssignLecturersRequest(@NotNull(message = "lecturerIds is required") List<UUID> lecturerIds) {
    }

    record LecturerRef(UUID userId, String username, String fullName, String email, boolean active) {
    }

    record CourseResponse(UUID courseId, String code, String name, String description, Language defaultLanguage,
                          boolean active, int lecturerCount, Instant createdAt, Instant updatedAt) {
    }

    record CourseDetail(UUID courseId, String code, String name, String description, Language defaultLanguage,
                        boolean active, List<LecturerRef> lecturers, Instant createdAt, Instant updatedAt) {
    }
}
