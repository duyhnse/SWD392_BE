package swd392.group6.AIVES.exam.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TemplateSummary(UUID id, long no, UUID courseId, String title, int mainQuestionCount, int totalDurationSec,
                       BigDecimal passScore, boolean locked, boolean archived, long usedByExamCount, int version,
                       Instant updatedAt) {
}
