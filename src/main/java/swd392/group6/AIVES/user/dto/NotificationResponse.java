package swd392.group6.AIVES.user.dto;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID notificationId, String type, String title, String body, JsonNode payload,
                            boolean read, Instant readAt, Instant createdAt) {
}
