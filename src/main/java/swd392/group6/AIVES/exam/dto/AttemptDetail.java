package swd392.group6.AIVES.exam.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttemptDetail(UUID attemptId, long attemptNo, UUID vivaExamId, UUID courseId, UUID studentId, String username,
                     String fullName, String studentCode, String status, String endReason, String cancelReason,
                     Instant startedAt, Instant deadlineAt, Instant endedAt, Instant consentRecordedAt,
                     int disconnectCount, int frozenSecTotal, String clientInfo, Long selectionSeed,
                     List<AttemptQuestionView> questions) {
}
