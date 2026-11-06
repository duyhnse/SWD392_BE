package swd392.group6.AIVES.questionbank.dto;

import swd392.group6.AIVES.questionbank.domain.QuestionOrigin;
import swd392.group6.AIVES.questionbank.domain.QuestionStatus;

import swd392.group6.AIVES.questionbank.BloomLevel;
import java.util.List;
import java.util.UUID;

public record QuestionFilter(List<QuestionStatus> status, List<UUID> topicId, List<BloomLevel> bloomLevel,
                             List<QuestionOrigin> origin, String q, UUID generationRequestId) {
}
