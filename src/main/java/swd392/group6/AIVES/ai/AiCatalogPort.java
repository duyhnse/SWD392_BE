package swd392.group6.AIVES.ai;

import tools.jackson.databind.JsonNode;

/** What the admin page shows about the AI node (D70): its model catalogue and provider health. */
public interface AiCatalogPort {

    /** {@code GET /v1/models}: {llm, stt, tts, embedding, voices, defaults, ranges}. */
    JsonNode models();

    /** {@code GET /v1/health}. */
    JsonNode health();
}
