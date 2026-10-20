package swd392.group6.AIVES.interview.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * What the student client needs after every call; also the heartbeat answer. Never contains reference answers,
 * AI analysis or scores (BR-I4). {@code stateVersion} changes with every recorded event, so the client notices a
 * skip / resume within one poll.
 */
public record AttemptState(UUID attemptId, String examTitle, String status, String endReason, int stateVersion,
                    Instant serverTime, Instant deadlineAt, int remainingSec, Progress progress, CurrentTurn currentTurn,
                    Notice notice, boolean showQuestionText, int silenceWarningSec, String cancelReason) {
}
