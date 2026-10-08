package swd392.group6.AIVES.ai;

import java.time.Duration;

/**
 * One chat completion that must answer with JSON only.
 *
 * @param systemPrompt rendered system prompt (templates live in resources/prompts, never inline in Java)
 * @param userPrompt   rendered user prompt; untrusted text must already be wrapped in its tags
 */
public record LlmRequest(LlmTask task, String systemPrompt, String userPrompt, double temperature, Duration timeout) {
}
