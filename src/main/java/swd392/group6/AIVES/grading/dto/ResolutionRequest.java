package swd392.group6.AIVES.grading.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolutionRequest(@NotBlank @Size(max = 4000) String resolution) {
}
