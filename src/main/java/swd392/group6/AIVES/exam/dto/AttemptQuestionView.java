package swd392.group6.AIVES.exam.dto;

import swd392.group6.AIVES.questionbank.BloomLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A drawn question with its snapshot (D49) — lecturers only. */
public record AttemptQuestionView(UUID attemptQuestionId, UUID questionId, int orderNo, String status, String topicName,
                           BloomLevel bloomLevel, String content, String referenceAnswer,
                           int timeBudgetSec, int timeUsedSec, String rubricName, Instant startedAt,
                           Instant endedAt, UUID replacesAttemptQuestionId, String voidReason,
                           BigDecimal maxPoints) {
}
