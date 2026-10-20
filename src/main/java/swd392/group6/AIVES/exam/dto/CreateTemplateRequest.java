package swd392.group6.AIVES.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

// ---- đề thi (exam templates) ----------------------------------------------------------------------------/** Missing numbers take the {@code exam_templates} defaults; the language is always the course language (D57). */
public record CreateTemplateRequest(@NotBlank @Size(max = 200) String title, String description,
                             Integer maxFollowupsPerQuestion, Integer maxAnswerSec, Integer silenceWarningSec,
                             Boolean showQuestionText,
                             @DecimalMin("0") @DecimalMax("10") BigDecimal passScore,
                             List<@Valid @NotNull TemplateItemRequest> items) {
}
