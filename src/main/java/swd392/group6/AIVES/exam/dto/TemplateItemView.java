package swd392.group6.AIVES.exam.dto;

import swd392.group6.AIVES.questionbank.BloomLevel;
import java.math.BigDecimal;
import java.util.UUID;

/** {@code available} = PUBLISHED questions of the pool that match the row right now. */
public record TemplateItemView(UUID id, UUID topicId, String topicName, BloomLevel bloomLevel,
                        int count, int secondsPerQuestion, int sortOrder, int available, BigDecimal pointsPerQuestion) {
}
