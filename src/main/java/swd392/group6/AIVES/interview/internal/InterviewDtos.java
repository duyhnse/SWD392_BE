package swd392.group6.AIVES.interview.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Request and response shapes of the interview runtime (05 §11). */
final class InterviewDtos {

    private InterviewDtos() {
    }

    /**
     * What the student client needs after every call; also the heartbeat answer. Never contains reference answers,
     * AI analysis or scores (BR-I4). {@code stateVersion} changes with every recorded event, so the client notices a
     * skip / resume within one poll.
     */
    record AttemptState(UUID attemptId, String examTitle, String status, String endReason, int stateVersion,
                        Instant serverTime, Instant deadlineAt, int remainingSec, Progress progress, CurrentTurn currentTurn,
                        Notice notice, boolean showQuestionText, int silenceWarningSec) {
    }

    /** {@code threadIndex} 1-based; {@code threadRemainingSec} = answering time left for this main question (D46). */
    record Progress(int threadIndex, int threadCount, int followupIndex, int maxFollowups, int threadRemainingSec) {
    }

    /**
     * @param text     always sent so the client can speak it when the audio is missing or silent; shown on screen
     *                 only when {@code showQuestionText} (the student hears the question it already received)
     * @param audioUrl null → the client speaks {@code text} with the browser's speech synthesis (EX-7)
     * @param status   ASKED (answer now) or PROCESSING (the AI node is working on the answer)
     */
    record CurrentTurn(UUID turnId, String type, String language, String text, String audioUrl, Integer audioMs,
                       Instant askedAt, int answerTimeLimitSec, String status) {
    }

    record Notice(String type, Instant at) {
    }

    record ReasonRequest(String reason) {
    }

    record NoAnswerRequest(String reason) {
    }

    /** Something the browser saw that belongs in the audit trail (silence, tab hidden, camera lost …). */
    record ClientEventRequest(String type, Map<String, Object> details) {
    }

    /** One attempt on the lecturer's live monitor (05 §7). */
    record LiveAttempt(UUID attemptId, UUID studentId, String status, String endReason, Instant startedAt,
                       Instant deadlineAt, Instant endedAt, int remainingSec, int threadIndex, int threadCount,
                       int followupIndex, int maxFollowups, int threadRemainingSec, String currentTurnType,
                       String currentTurnStatus, String currentQuestionText, Instant lastSeenAt, boolean online,
                       Instant disconnectedSince, int disconnectCount, int frozenSecTotal, Integer lastProcessingMs,
                       int turnCount, int recordingChunks, Instant lastRecordingAt, int eventCount) {
    }
}
