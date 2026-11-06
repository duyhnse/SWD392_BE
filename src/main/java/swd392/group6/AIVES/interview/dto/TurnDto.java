package swd392.group6.AIVES.interview.dto;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * One question → answer. {@code followupDecision}, {@code aiAnalysis} and {@code processingMs} are only
 * filled for lecturers (students never see AI analysis — 15 §6).
 */
public record TurnDto(UUID turnId, UUID attemptQuestionId, int threadOrderNo, int turnOrder, String type, int followupIndex,
               String questionText, String language, String status, Instant askedAt, Instant answerSubmittedAt,
               Integer responseDurationSec, String transcript, String followupDecision, JsonNode aiAnalysis,
               Integer processingMs, String questionAudioUrl, String answerAudioUrl) {
}
