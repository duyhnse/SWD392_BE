package swd392.group6.AIVES.user.dto;

import swd392.group6.AIVES.ai.AiSettings;
import tools.jackson.databind.JsonNode;
import java.time.Instant;

/** @param rule how an {@code ai.*} value is edited (range, choices, model group); null for the other settings */
public record SettingResponse(String key, JsonNode value, String description, Instant updatedAt, AiSettings.Rule rule) {
}
