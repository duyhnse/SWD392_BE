package swd392.group6.AIVES.interview.internal;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Response shapes of the lượt thi record endpoints (15 §5.4). */
final class SessionRecordDtos {

    private SessionRecordDtos() {
    }

    /**
     * One question → answer. {@code followupDecision}, {@code aiAnalysis} and {@code processingMs} are only
     * filled for lecturers (students never see AI analysis — 15 §6).
     */
    record TurnDto(UUID turnId, UUID sessionQuestionId, int threadOrderNo, int turnOrder, String type, int followupIndex,
                   String questionText, String language, String status, Instant askedAt, Instant answerSubmittedAt,
                   Integer responseDurationSec, String transcript, String followupDecision, JsonNode aiAnalysis,
                   Integer processingMs, String questionAudioUrl, String answerAudioUrl) {
    }

    record EventDto(UUID id, UUID turnId, String type, UUID actorId, JsonNode payload, Instant createdAt) {
    }
}
