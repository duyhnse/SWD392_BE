package swd392.group6.AIVES.questionbank.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CriterionDto(UUID id, String name, String description, BigDecimal maxScore, BigDecimal weightPercent,
                           int sortOrder) {
}
