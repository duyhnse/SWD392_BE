package swd392.group6.AIVES.grading.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code confirmedByName}: staff only — the student's result never names a grader. */
public record EvaluationDto(UUID id, UUID attemptId, UUID vivaExamId, String status, int version, StudentRef student,
                     BigDecimal aiTotalScore, BigDecimal finalTotalScore, BigDecimal currentTotalScore,
                     String aiGeneralFeedback, String lecturerComment, UUID confirmedBy, Instant confirmedAt,
                     Instant createdAt, Instant updatedAt, List<ThreadDto> threads, String confirmedByName) {
}
