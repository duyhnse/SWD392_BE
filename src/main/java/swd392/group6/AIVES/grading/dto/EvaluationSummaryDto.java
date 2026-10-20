package swd392.group6.AIVES.grading.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EvaluationSummaryDto(UUID attemptId, Long attemptNo, StudentRef student, String attemptStatus, Instant endedAt,
                            UUID evaluationId, String evaluationStatus, BigDecimal aiTotalScore,
                            BigDecimal finalTotalScore, int aiFailedThreads, int missingDataThreads,
                            boolean openDispute, int aiNotRequestedThreads) {
}
