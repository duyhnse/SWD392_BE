package swd392.group6.AIVES.user.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/**
 * {@code GET /admin/users/{id}}: the profile plus role details — assigned courses for a lecturer,
 * number of lượt thi for a student (null when not applicable).
 */
public record UserDetail(@JsonUnwrapped UserResponseDTO user, List<CourseRef> courses, Long examAttemptCount) {
}
