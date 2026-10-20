package swd392.group6.AIVES.questionbank.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ---- AI generation (04 §9.2) ----
public record GenerationRequest(@NotNull UUID topicId, @NotEmpty @Size(max = 20) List<@NotNull UUID> materialIds,
                                @NotNull @Min(1) @Max(20) Integer count,
                                @NotEmpty List<@NotNull BloomLevel> bloomLevels,
                                @Size(max = 1000) String focusText, Language language, UUID rubricId,
                                Map<BloomLevel, UUID> rubricsByBloom) {
    /** {@code rubricsByBloom}: rubric per question type (Bloom level); a level without one takes {@code rubricId}. */
}
