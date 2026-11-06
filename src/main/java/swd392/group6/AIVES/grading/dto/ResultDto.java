package swd392.group6.AIVES.grading.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResultDto(UUID evaluationId, UUID attemptId, UUID vivaExamId, String examTitle, BigDecimal finalTotalScore,
                 String lecturerComment, Instant confirmedAt, Instant resultsReleasedAt, Instant disputeDeadline,
                 boolean canDispute, List<ResultThreadDto> threads) {
}
