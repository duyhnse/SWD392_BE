package swd392.group6.AIVES.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import swd392.group6.AIVES.common.Language;

public record CreateCourseRequest(
        @NotBlank(message = "Code is required") @Size(max = 20) String code,
        @NotBlank(message = "Name is required") @Size(max = 150) String name,
        String description,
        Language defaultLanguage
) {
}
