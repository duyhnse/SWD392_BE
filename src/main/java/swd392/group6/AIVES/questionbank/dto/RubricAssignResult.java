package swd392.group6.AIVES.questionbank.dto;

import java.util.List;
import java.util.UUID;

/** {@code failed}: questions left unchanged, with why ({@code QUESTION_LOCKED}, {@code INVALID_QUESTION_STATE}, …). */
public record RubricAssignResult(List<UUID> updated, List<PublishFailure> failed) {
}
