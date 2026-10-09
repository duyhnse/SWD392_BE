package swd392.group6.AIVES.interview.internal;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Response shapes of the lượt thi record endpoints (15 §5.4). */
final class AttemptRecordDtos {

    private AttemptRecordDtos() {
    }

    /**
     * One question → answer. {@code followupDecision}, {@code aiAnalysis} and {@code processingMs} are only
     * filled for lecturers (students never see AI analysis — 15 §6).
     */
    record TurnDto(UUID turnId, UUID attemptQuestionId, int threadOrderNo, int turnOrder, String type, int followupIndex,
                   String questionText, String language, String status, Instant askedAt, Instant answerSubmittedAt,
                   Integer responseDurationSec, String transcript, String followupDecision, JsonNode aiAnalysis,
                   Integer processingMs, String questionAudioUrl, String answerAudioUrl) {
    }

    record RecordingDto(UUID id, String kind, int chunkIndex, String contentType, long sizeBytes, Integer durationMs,
                        String sha256, Instant clientStartedAt, Instant uploadedAt, String fileUrl) {
    }

    record EventDto(UUID id, UUID turnId, String type, UUID actorId, JsonNode payload, Instant createdAt) {
    }
}
