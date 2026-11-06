package swd392.group6.AIVES.interview.dto;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventDto(UUID id, UUID turnId, String type, UUID actorId, JsonNode payload, Instant createdAt) {
}
