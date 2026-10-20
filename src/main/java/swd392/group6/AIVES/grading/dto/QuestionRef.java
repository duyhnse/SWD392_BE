package swd392.group6.AIVES.grading.dto;

import swd392.group6.AIVES.questionbank.BloomLevel;
import java.util.UUID;

/** {@code language}: the language this question was asked and graded in (BR-Q9). */
public record QuestionRef(UUID id, String content, String referenceAnswer, BloomLevel bloomLevel, String language) {
}
