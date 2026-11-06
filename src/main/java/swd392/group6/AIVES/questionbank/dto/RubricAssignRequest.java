package swd392.group6.AIVES.questionbank.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Attach one rubric to many questions of the course at once (rubric by question type). */
public record RubricAssignRequest(@NotEmpty @Size(max = 500) List<@NotNull UUID> questionIds, @NotNull UUID rubricId) {
}
