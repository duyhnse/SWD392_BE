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
                                @NotNull Map<@NotNull BloomLevel, @NotNull @Min(0) @Max(20) Integer> countsByBloom,
                                @Size(max = 1000) String focusText, Language language) {
    /** {@code countsByBloom}: how many questions of each Bloom level; 0 or absent = none of that level, 1–20 in all (D82). */
}
