package swd392.group6.AIVES.questionbank.dto;

import java.time.Instant;
import java.util.UUID;

public record TopicDto(UUID id, UUID courseId, String name, String description, int sortOrder,
                       long questionCount, UUID createdBy, Instant createdAt) {
}
