package swd392.group6.AIVES.exam.dto;

import swd392.group6.AIVES.exam.domain.ExamStage;
import swd392.group6.AIVES.exam.domain.ExamStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExamDetail(UUID id, long no, UUID courseId, String title, String description, String instructions, String location,
                  ExamStatus status, UUID createdBy, UUID examinerId, TemplateSummary template,
                  Instant checkinOpensAt, Instant checkinClosesAt, int durationSec, Instant lastPossibleEndAt,
                  int reconnectGraceSec, int maxDisconnects, int maxFrozenSec, int replaceMainAfterSec,
                  long studentCount, Map<ExamStage, Long> stageCounts, boolean resultsReleased,
                  Instant resultsReleasedAt, UUID retakeOfVivaExamId, String cancelReason, int version,
                  Instant createdAt, Instant updatedAt) {
}
