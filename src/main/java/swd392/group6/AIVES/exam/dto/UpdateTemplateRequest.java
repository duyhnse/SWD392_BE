package swd392.group6.AIVES.exam.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Full replacement of the template's own fields (rows and pool have their own endpoints). */
public record UpdateTemplateRequest(@NotNull Integer version, @NotBlank @Size(max = 200) String title, String description,
                             @NotNull Integer maxFollowupsPerQuestion,
                             @NotNull Integer maxAnswerSec, @NotNull Integer silenceWarningSec,
                             @NotNull Boolean showQuestionText,
                             @DecimalMin("0") @DecimalMax("10") BigDecimal passScore) {
}
