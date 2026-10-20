package swd392.group6.AIVES.grading.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CriterionDto(UUID criterionScoreId, UUID criterionId, String name, String description, BigDecimal maxScore,
                    BigDecimal weightPercent, BigDecimal aiScore, String aiJustification, BigDecimal finalScore) {
}
