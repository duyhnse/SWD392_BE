package swd392.group6.AIVES.exam;

import swd392.group6.AIVES.exam.ExamApi.AttemptInfo;
import swd392.group6.AIVES.exam.ExamApi.AttemptQuestionInfo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes on an attempt's own rows for the interview runtime (WF3, 05 §6–§7, D46, D51). The exam module owns
 * {@code exam_attempts} and {@code attempt_questions}; the interview module decides <em>when</em> to change them and
 * calls these methods inside its own transaction (they join it). Status values are the DB enum names.
 */
public interface AttemptControlApi {

    /** Locks the attempt row ({@code select … for update}) for the rest of the caller's transaction. */
    Optional<AttemptInfo> lock(UUID attemptId);

    /** Heartbeat from the student client. */
    void touch(UUID attemptId, Instant at);

    /** No heartbeat since {@code since}: the clock is frozen from then on (D51). */
    void markDisconnected(UUID attemptId, Instant since);

    /** Back online after {@code offlineSec}: one more disconnect, frozen time added to the total and the deadline. */
    void reconnect(UUID attemptId, int offlineSec, Instant now);

    /** Moves the deadline by time that is never charged to the student (playback, AI processing — D46). */
    void extendDeadline(UUID attemptId, int seconds);

    /** Too many / too long disconnects: waits for the examiner. The clock stays frozen until {@link #resume}. */
    void interrupt(UUID attemptId, Instant at);

    /** Examiner resumes: the frozen time since the disconnect is added to the deadline. */
    void resume(UUID attemptId, Instant now);

    /** Attempt finished ({@code ALL_QUESTIONS_DONE}, {@code TIME_UP}, {@code ENDED_BY_LECTURER}). */
    void complete(UUID attemptId, String endReason, Instant at);

    void startQuestion(UUID attemptQuestionId, Instant at);

    /** {@code DONE}, {@code SKIPPED} or {@code NOT_REACHED}. */
    void finishQuestion(UUID attemptQuestionId, String status, Instant at);

    /** Adds answering time to the question's budget use. */
    void chargeTime(UUID attemptQuestionId, int seconds);

    /**
     * Replaces a question after a long disconnect (D51): it becomes {@code VOIDED} and another question of the same
     * đề thi row is drawn and snapshotted under the same order number. Empty (nothing changed) when the row has no
     * other question left.
     */
    Optional<AttemptQuestionInfo> replaceQuestion(UUID attemptQuestionId, String voidReason, Instant at);
}
