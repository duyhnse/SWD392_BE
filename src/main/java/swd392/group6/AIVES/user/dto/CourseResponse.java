package swd392.group6.AIVES.user.dto;

import swd392.group6.AIVES.common.Language;
import java.time.Instant;
import java.util.UUID;

public record CourseResponse(UUID courseId, String code, String name, String description, Language defaultLanguage,
                      boolean active, int lecturerCount, Instant createdAt, Instant updatedAt) {
}
