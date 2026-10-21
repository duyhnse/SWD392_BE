package swd392.group6.AIVES.grading.dto;

import swd392.group6.AIVES.grading.service.GradingJson;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code maxPoints}: points of this question in the total (D81), null = equal weight; {@code finalPoints} = finalScore / 10 × maxPoints. */
public record ThreadDto(UUID gradeId, UUID attemptQuestionId, int orderNo, String status, boolean includeInTotal,
                 QuestionRef question, RubricRef rubric, List<TurnDto> turns, List<CriterionDto> criteria,
                 BigDecimal aiScore, BigDecimal finalScore, List<String> aiStrengths, List<String> aiWeaknesses,
                 List<String> aiMissingPoints, String aiFeedback, String aiError, GradingJson.Signals signals,
                 String lecturerComment, UUID confirmedBy, Instant confirmedAt, String confirmedByName,
                 BigDecimal maxPoints, BigDecimal finalPoints) {
}
