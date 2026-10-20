package swd392.group6.AIVES.grading.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CriterionFinal(@NotNull UUID criterionId, @NotNull BigDecimal finalScore) {
}
