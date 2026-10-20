package swd392.group6.AIVES.questionbank.dto;

import swd392.group6.AIVES.questionbank.domain.MaterialStatus;

import java.time.Instant;
import java.util.UUID;

// ---- Materials ----
public record MaterialDto(UUID id, UUID courseId, UUID topicId, String topicName, String fileName, String contentType,
                          long sizeBytes,
                          MaterialStatus status, Integer pageCount, String errorMessage, UUID uploadedBy,
                          Instant createdAt, Instant indexedAt) {
}
