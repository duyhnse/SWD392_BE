package swd392.group6.AIVES.questionbank.dto;

import swd392.group6.AIVES.questionbank.domain.QuestionOrigin;
import swd392.group6.AIVES.questionbank.domain.QuestionStatus;

import com.fasterxml.jackson.annotation.JsonProperty;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuestionDto(UUID id, long no, UUID courseId, UUID topicId, String topicName, String content,
                          String referenceAnswer, BloomLevel bloomLevel, Language language,
                          QuestionStatus status, QuestionOrigin origin, QuestionRubricDto rubric, AiDto ai,
                          List<SourceDto> sources, @JsonProperty("isLocked") boolean isLocked, UUID ownerId,
                          UUID supersedesQuestionId, Integer version, Instant createdAt, Instant updatedAt,
                          UUID publishedBy, Instant publishedAt, Instant discardedAt, String ownerName,
                          String publishedByName) {
}
