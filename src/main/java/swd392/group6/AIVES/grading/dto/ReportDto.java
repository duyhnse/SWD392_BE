package swd392.group6.AIVES.grading.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @param studentsByStage every roster student by derived stage (no attempt → UPCOMING / AVAILABLE / MISSED)
 * @param passedCount     confirmed totals ≥ {@code passScore}; null when the đề thi has no pass mark
 */
public record ReportDto(UUID vivaExamId, String title, int rosterSize, int totalAttempts,
                 Map<String, Integer> attemptsByStatus, Map<String, Integer> studentsByStage, int evaluatedCount,
                 int confirmedCount, ScoreStats finalTotals, BigDecimal passScore, Integer passedCount,
                 List<Bin> distribution, List<QuestionStats> questions, List<QuestionStats> hardestQuestions) {
}
