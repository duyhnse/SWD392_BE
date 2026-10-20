package swd392.group6.AIVES.questionbank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// ---- Rubrics ----
public record CriterionRequest(@NotBlank @Size(max = 150) String name, @Size(max = 1000) String description,
                               @NotNull BigDecimal maxScore, @NotNull BigDecimal weightPercent, Integer sortOrder) {
}
