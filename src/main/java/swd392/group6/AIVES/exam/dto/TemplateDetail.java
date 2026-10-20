package swd392.group6.AIVES.exam.dto;

import swd392.group6.AIVES.exam.domain.QuestionPoolMode;

import swd392.group6.AIVES.common.Language;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TemplateDetail(UUID id, long no, UUID courseId, String title, String description, Language language,
                      int maxFollowupsPerQuestion, int maxAnswerSec, int silenceWarningSec,
                      boolean showQuestionText, BigDecimal passScore,
                      QuestionPoolMode questionPoolMode, List<UUID> selectedQuestionIds,
                      List<TemplateItemView> items, int mainQuestionCount, int totalDurationSec,
                      boolean poolSufficient, boolean locked, boolean archived, long usedByExamCount,
                      UUID createdBy, int version, Instant createdAt, Instant updatedAt) {
}
