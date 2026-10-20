package swd392.group6.AIVES.grading.dto;

import java.time.Instant;
import java.util.UUID;

public record ChangeDto(UUID id, UUID questionGradeId, Integer threadOrderNo, UUID criterionScoreId, String field,
                 String oldValue, String newValue, UUID changedBy, String changedByName, Instant changedAt) {
}
