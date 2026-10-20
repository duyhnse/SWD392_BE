package swd392.group6.AIVES.user.dto;

import jakarta.validation.constraints.Size;
import swd392.group6.AIVES.common.Language;

public record UpdateCourseRequest(
        @Size(max = 20) String code,
        @Size(max = 150) String name,
        String description,
        Language defaultLanguage,
        Boolean active
) {
}
