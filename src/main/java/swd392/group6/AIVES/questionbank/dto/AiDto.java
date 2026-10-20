package swd392.group6.AIVES.questionbank.dto;

import swd392.group6.AIVES.questionbank.BloomLevel;
import tools.jackson.databind.JsonNode;
import java.util.UUID;

public record AiDto(String originalContent, String originalReferenceAnswer, BloomLevel suggestedBloom,
                    JsonNode suggestedRubric, UUID generationRequestId) {
}
