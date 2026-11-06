package swd392.group6.AIVES.grading.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ResultThreadDto(UUID gradeId, int orderNo, String questionContent, boolean includeInTotal,
                       BigDecimal finalScore, List<ResultCriterionDto> criteria, List<String> aiStrengths,
                       List<String> aiWeaknesses, List<String> aiMissingPoints, String aiFeedback,
                       String lecturerComment, BigDecimal maxPoints, BigDecimal finalPoints) {
}
