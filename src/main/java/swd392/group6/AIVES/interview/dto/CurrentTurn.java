package swd392.group6.AIVES.interview.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param text     always sent so the client can speak it when the audio is missing or silent; shown on screen
 *                 only when {@code showQuestionText} (the student hears the question it already received)
 * @param audioUrl null → the client speaks {@code text} with the browser's speech synthesis (EX-7)
 * @param status   ASKED (answer now) or PROCESSING (the AI node is working on the answer)
 * @param audioPending the follow-up voice is being made (D70): poll again for {@code audioUrl}, then speak the text
 */
public record CurrentTurn(UUID turnId, String type, String language, String text, String audioUrl, Integer audioMs,
                   Instant askedAt, int answerTimeLimitSec, String status, boolean audioPending) {
}
