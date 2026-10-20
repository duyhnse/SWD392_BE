package swd392.group6.AIVES.grading.dto;

import java.math.BigDecimal;

public record ResultCriterionDto(String name, BigDecimal maxScore, BigDecimal weightPercent, BigDecimal finalScore) {
}
