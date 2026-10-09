package swd392.group6.AIVES.grading;

import java.util.UUID;

/**
 * Public API of the grading module. The lecturer endpoint {@code POST /attempts/{id}/evaluation} and the future
 * AI grading pipeline (on {@code AttemptCompletedEvent}) create evaluations through the same method.
 */
public interface GradingApi {

    /**
     * Creates the evaluation of a COMPLETED lượt thi: one question grade per thread with a rubric snapshot and one
     * empty criterion score per criterion (06 §4). Idempotent: an existing evaluation is returned unchanged.
     *
     * @throws swd392.group6.AIVES.common.ApiException 404 {@code ATTEMPT_NOT_FOUND}, 409 {@code ATTEMPT_NOT_COMPLETED},
     *                                                 422 {@code RUBRIC_MISSING}
     */
    CreatedEvaluation createEvaluation(UUID attemptId, Mode mode);

    /**
     * {@code AI}: threads with a transcript stay {@code PENDING_AI} for the AI pipeline and the evaluation is
     * {@code PENDING_AI} while any of them is. {@code MANUAL}: no AI run is planned; such threads are marked
     * {@code AI_FAILED} with {@code ai_error = AI_NOT_REQUESTED} and the evaluation is {@code AWAITING_REVIEW}.
     */
    enum Mode {
        AI,
        MANUAL
    }

    record CreatedEvaluation(UUID evaluationId, String status, boolean created) {
    }
}
