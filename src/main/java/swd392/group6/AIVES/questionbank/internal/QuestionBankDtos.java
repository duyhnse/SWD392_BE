package swd392.group6.AIVES.questionbank.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request / response bodies of the question bank API (09 §3–§4, 15 §5.2). */
public final class QuestionBankDtos {

    private QuestionBankDtos() {
    }

    // ---- Topics & terms ----

    public record CreateTopicRequest(@NotBlank @Size(max = 150) String name, String description, Integer sortOrder) {
    }

    public record UpdateTopicRequest(@Size(max = 150) String name, String description, Integer sortOrder) {
    }

    public record TopicDto(UUID id, UUID courseId, String name, String description, int sortOrder,
                           long questionCount, UUID createdBy, Instant createdAt) {
    }

    public record TermsDto(@NotNull List<String> terms) {
    }

    // ---- Rubrics ----

    public record CriterionRequest(@NotBlank @Size(max = 150) String name, @Size(max = 1000) String description,
                                   @NotNull BigDecimal maxScore, @NotNull BigDecimal weightPercent, Integer sortOrder) {
    }

    public record RubricRequest(@NotBlank @Size(max = 150) String name, @Size(max = 1000) String description,
                                @NotNull @Valid List<CriterionRequest> criteria) {
    }

    public record DuplicateRubricRequest(@Size(max = 150) String name) {
    }

    public record CriterionDto(UUID id, String name, String description, BigDecimal maxScore, BigDecimal weightPercent,
                               int sortOrder) {
    }

    public record RubricDto(UUID id, long no, UUID courseId, String name, String description,
                            @JsonProperty("isLocked") boolean isLocked, BigDecimal totalWeight, long questionCount,
                            List<CriterionDto> criteria, UUID createdBy, Instant createdAt, Instant updatedAt) {
    }

    // ---- Questions ----

    public record CreateQuestionRequest(@NotNull UUID topicId, @NotNull @Size(max = 4000) String content,
                                        @Size(max = 4000) String referenceAnswer, BloomLevel bloomLevel,
                                        Language language, UUID rubricId) {
    }

    public record UpdateQuestionRequest(@NotNull Integer version, @NotNull UUID topicId,
                                        @NotNull @Size(max = 4000) String content,
                                        @Size(max = 4000) String referenceAnswer, BloomLevel bloomLevel,
                                        Language language, UUID rubricId) {
    }

    public record PublishRequest(@NotEmpty @Size(max = 200) List<@NotNull UUID> questionIds) {
    }

    public record PublishResult(List<UUID> published, List<PublishFailure> failed) {
    }

    public record PublishFailure(UUID id, List<String> errors) {
    }

    public record QuestionRubricDto(UUID id, String name, @JsonProperty("isLocked") boolean isLocked,
                                    BigDecimal totalWeight, List<CriterionDto> criteria) {
    }

    public record AiDto(String originalContent, String originalReferenceAnswer, BloomLevel suggestedBloom,
                        JsonNode suggestedRubric, UUID generationRequestId) {
    }

    public record SourceDto(UUID id, UUID materialId, String materialName, UUID chunkId, String locationLabel,
                            String excerpt) {
    }

    public record QuestionDto(UUID id, long no, UUID courseId, UUID topicId, String topicName, String content,
                              String referenceAnswer, BloomLevel bloomLevel, Language language,
                              QuestionStatus status, QuestionOrigin origin, QuestionRubricDto rubric, AiDto ai,
                              List<SourceDto> sources, @JsonProperty("isLocked") boolean isLocked, UUID ownerId,
                              UUID supersedesQuestionId, Integer version, Instant createdAt, Instant updatedAt,
                              UUID publishedBy, Instant publishedAt, Instant discardedAt) {
    }

    public record QuestionSummaryDto(UUID id, long no, UUID courseId, UUID topicId, String topicName,
                                     String content, BloomLevel bloomLevel, Language language, QuestionStatus status,
                                     QuestionOrigin origin, UUID rubricId, String rubricName,
                                     @JsonProperty("isLocked") boolean isLocked, UUID ownerId, Integer version,
                                     Instant createdAt, Instant updatedAt, Instant publishedAt) {
    }

    public record QuestionFilter(List<QuestionStatus> status, List<UUID> topicId, List<BloomLevel> bloomLevel,
                                 List<QuestionOrigin> origin, String q) {
    }

    // ---- Materials ----

    public record MaterialDto(UUID id, UUID courseId, String fileName, String contentType, long sizeBytes,
                              MaterialStatus status, Integer pageCount, String errorMessage, UUID uploadedBy,
                              Instant createdAt, Instant indexedAt) {
    }

    public record MaterialFile(String fileName, String contentType, byte[] data) {
    }
}
