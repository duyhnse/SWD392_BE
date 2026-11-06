package swd392.group6.AIVES.questionbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuestionRubricDto(UUID id, String name, @JsonProperty("isLocked") boolean isLocked,
                                BigDecimal totalWeight, List<CriterionDto> criteria) {
}
