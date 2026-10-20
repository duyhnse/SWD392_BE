package swd392.group6.AIVES.grading.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateEvaluationRequest(@NotNull Integer version, @Size(max = 4000) String lecturerComment) {
}
