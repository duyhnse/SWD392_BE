package swd392.group6.AIVES.user.dto;

import tools.jackson.databind.JsonNode;
import java.util.Map;

public record UpdateSettingsRequest(Map<String, JsonNode> settings) {
}
