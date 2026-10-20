package swd392.group6.AIVES.questionbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RubricDto(UUID id, long no, UUID courseId, String name, String description,
                        @JsonProperty("isLocked") boolean isLocked, BigDecimal totalWeight, long questionCount,
                        List<CriterionDto> criteria, UUID createdBy, Instant createdAt, Instant updatedAt) {
}
