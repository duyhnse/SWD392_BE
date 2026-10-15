package swd392.group6.AIVES.interview;

import java.util.List;
import java.util.UUID;

/**
 * An attempt finished (05 §13, BR-I9): published once, inside the transaction that completes it. WF4 starts grading
 * from here.
 *
 * @param reason {@code ALL_QUESTIONS_DONE}, {@code TIME_UP} or {@code ENDED_BY_LECTURER}
 */
public record AttemptCompletedEvent(UUID attemptId, UUID courseId, List<UUID> askedQuestionIds, String reason) {
}
