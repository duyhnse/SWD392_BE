package swd392.group6.AIVES.interview.dto;

import java.time.Instant;
import java.util.UUID;

/** One attempt on the lecturer's live monitor (05 §7). */
public record LiveAttempt(UUID attemptId, UUID studentId, String status, String endReason, Instant startedAt,
                   Instant deadlineAt, Instant endedAt, int remainingSec, int threadIndex, int threadCount,
                   int followupIndex, int maxFollowups, int threadRemainingSec, String currentTurnType,
                   String currentTurnStatus, String currentQuestionText, Instant lastSeenAt, boolean online,
                   Instant disconnectedSince, int disconnectCount, int frozenSecTotal, Integer lastProcessingMs,
                   int turnCount, int recordingChunks, Instant lastRecordingAt, int eventCount,
                   String cancelReason, Instant cameraFrameAt, Instant screenFrameAt) {
}
