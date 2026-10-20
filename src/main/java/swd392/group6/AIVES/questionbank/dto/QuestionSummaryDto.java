package swd392.group6.AIVES.questionbank.dto;

import swd392.group6.AIVES.questionbank.domain.QuestionOrigin;
import swd392.group6.AIVES.questionbank.domain.QuestionStatus;

import com.fasterxml.jackson.annotation.JsonProperty;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;
import java.time.Instant;
import java.util.UUID;

public record QuestionSummaryDto(UUID id, long no, UUID courseId, UUID topicId, String topicName,
                                 String content, BloomLevel bloomLevel, Language language, QuestionStatus status,
                                 QuestionOrigin origin, UUID rubricId, String rubricName,
                                 @JsonProperty("isLocked") boolean isLocked, UUID ownerId, Integer version,
                                 Instant createdAt, Instant updatedAt, Instant publishedAt, String ownerName) {
}
