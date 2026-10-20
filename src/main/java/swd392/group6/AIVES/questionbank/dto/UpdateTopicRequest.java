package swd392.group6.AIVES.questionbank.dto;

import jakarta.validation.constraints.Size;

public record UpdateTopicRequest(@Size(max = 150) String name, String description, Integer sortOrder) {
}
