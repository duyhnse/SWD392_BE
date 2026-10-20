package swd392.group6.AIVES.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import swd392.group6.AIVES.questionbank.BloomLevel;
import java.util.UUID;

/** {@code secondsPerQuestion} null = default for the Bloom level ({@code exam.seconds.*} settings). */
public record TemplateItemRequest(UUID topicId, BloomLevel bloomLevel, @NotNull @Min(1) @Max(10) Integer count,
                           Integer secondsPerQuestion) {
}
