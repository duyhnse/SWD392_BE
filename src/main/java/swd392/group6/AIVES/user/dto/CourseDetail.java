package swd392.group6.AIVES.user.dto;

import swd392.group6.AIVES.common.Language;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CourseDetail(UUID courseId, String code, String name, String description, Language defaultLanguage,
                    boolean active, List<LecturerRef> lecturers, Instant createdAt, Instant updatedAt) {
}
