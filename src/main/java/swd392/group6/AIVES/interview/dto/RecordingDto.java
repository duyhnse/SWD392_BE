package swd392.group6.AIVES.interview.dto;

import java.time.Instant;
import java.util.UUID;

public record RecordingDto(UUID id, String kind, int chunkIndex, String contentType, long sizeBytes, Integer durationMs,
                    String sha256, Instant clientStartedAt, Instant uploadedAt, String fileUrl) {
}
