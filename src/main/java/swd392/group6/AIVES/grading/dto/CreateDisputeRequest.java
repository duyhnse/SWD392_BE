package swd392.group6.AIVES.grading.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

// ----- disputes -------------------------------------------------------------------------------------------------
public record CreateDisputeRequest(@NotBlank @Size(max = 4000) String reason, List<UUID> questionGradeIds) {
}
