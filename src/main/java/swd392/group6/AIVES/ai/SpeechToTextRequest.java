package swd392.group6.AIVES.ai;

import swd392.group6.AIVES.common.Language;

/**
 * @param hotwordPrompt  course terms prompt (10_AI_PROMPTS.md §6), may be empty
 * @param mockTranscript dev/test only: text the mock adapter returns (from header X-Mock-Transcript); ignored by real adapters
 */
public record SpeechToTextRequest(byte[] audio, String contentType, Language language, String hotwordPrompt,
                                  String mockTranscript) {
}
