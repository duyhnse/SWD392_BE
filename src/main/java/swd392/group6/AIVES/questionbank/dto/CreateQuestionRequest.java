package swd392.group6.AIVES.questionbank.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;
import java.util.UUID;

// ---- Questions ----
public record CreateQuestionRequest(@NotNull UUID topicId, @NotNull @Size(max = 4000) String content,
                                    @Size(max = 4000) String referenceAnswer, BloomLevel bloomLevel,
                                    Language language, UUID rubricId) {
}
