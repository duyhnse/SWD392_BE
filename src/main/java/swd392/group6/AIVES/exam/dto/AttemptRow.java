package swd392.group6.AIVES.exam.dto;

import swd392.group6.AIVES.exam.domain.ExamStage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One roster row of a buổi thi as the lecturer sees it, with the attempt when there is one. */
public record AttemptRow(UUID studentId, String username, String fullName, String studentCode, int seqNo,
                  ExamStage stage, UUID attemptId, Long attemptNo, String status, String endReason, Instant startedAt,
                  Instant deadlineAt, Instant endedAt, int disconnectCount, int frozenSecTotal,
                  UUID evaluationId, String evaluationStatus, BigDecimal finalTotalScore) {
}
