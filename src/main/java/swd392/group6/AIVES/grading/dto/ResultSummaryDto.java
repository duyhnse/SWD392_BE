package swd392.group6.AIVES.grading.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// ----- student results ----------------------------------------------------------------------------------------
public record ResultSummaryDto(UUID evaluationId, UUID attemptId, UUID vivaExamId, String examTitle,
                        BigDecimal finalTotalScore, Instant confirmedAt, Instant resultsReleasedAt) {
}
