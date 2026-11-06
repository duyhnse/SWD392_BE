package swd392.group6.AIVES.questionbank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// ---- Topics & terms ----
public record CreateTopicRequest(@NotBlank @Size(max = 150) String name, String description, Integer sortOrder) {
}
