package swd392.group6.AIVES.grading.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record QuestionStats(UUID questionId, String content, int timesAsked, int gradedCount, BigDecimal averageFinalScore,
                     BigDecimal goodAnswerRate) {
}
