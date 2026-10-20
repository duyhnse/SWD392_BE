package swd392.group6.AIVES.questionbank.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MoveRubricQuestionsRequest(@NotNull UUID targetRubricId) {
}
