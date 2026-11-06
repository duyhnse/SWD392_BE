package swd392.group6.AIVES.exam.dto;

import swd392.group6.AIVES.exam.domain.QuestionPoolMode;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record QuestionPoolRequest(@NotNull QuestionPoolMode mode, List<UUID> questionIds) {
}
