package swd392.group6.AIVES.exam;

import java.time.Instant;
import java.util.UUID;

/**
 * A student checked in and their questions were drawn (D48). Published inside the check-in transaction; the interview
 * module records it in {@code attempt_events}.
 */
public record AttemptStartedEvent(UUID attemptId, UUID vivaExamId, UUID studentId, Instant startedAt, long selectionSeed) {
}
