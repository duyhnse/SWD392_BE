package swd392.group6.AIVES.grading.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DisputeDto(UUID id, UUID evaluationId, UUID attemptId, UUID vivaExamId, String examTitle, StudentRef student,
                  String reason, List<UUID> questionGradeIds, String status, String resolution, UUID resolvedBy,
                  Instant createdAt, Instant resolvedAt, String evaluationStatus) {
}
