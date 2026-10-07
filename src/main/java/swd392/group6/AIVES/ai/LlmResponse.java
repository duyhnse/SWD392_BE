package swd392.group6.AIVES.ai;

/** Raw JSON text returned by the model. Callers parse and validate it (10_AI_PROMPTS.md §1). */
public record LlmResponse(String content, String model, long latencyMs) {
}
