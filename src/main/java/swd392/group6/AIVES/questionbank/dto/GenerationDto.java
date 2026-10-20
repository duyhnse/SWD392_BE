package swd392.group6.AIVES.questionbank.dto;

import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GenerationDto(UUID id, UUID courseId, UUID topicId, String topicName, List<UUID> materialIds,
                            int requestedCount, List<BloomLevel> bloomLevels, String focusText, Language language,
                            String status, int generatedCount, String message, UUID requestedBy,
                            Instant createdAt, Instant finishedAt, UUID rubricId, String rubricName,
                            Map<BloomLevel, UUID> rubricsByBloom) {
}
