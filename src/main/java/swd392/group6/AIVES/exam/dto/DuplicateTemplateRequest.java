package swd392.group6.AIVES.exam.dto;

import jakarta.validation.constraints.Size;

public record DuplicateTemplateRequest(@Size(max = 200) String title) {
}
