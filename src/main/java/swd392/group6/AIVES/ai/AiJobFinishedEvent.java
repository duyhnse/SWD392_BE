package swd392.group6.AIVES.ai;

import java.util.UUID;

/**
 * An AI job reached SUCCEEDED, FAILED or TIMED_OUT. Published after the result is stored; listeners read it with
 * {@link AiJobService#find(UUID)}. {@code subjectType}/{@code subjectId} are what the job was enqueued for
 * (e.g. {@code QUESTION_GRADE} + question grade id).
 */
public record AiJobFinishedEvent(UUID jobId, AiJobType type, String subjectType, UUID subjectId, String status) {
}
