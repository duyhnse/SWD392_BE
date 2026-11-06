package swd392.group6.AIVES.questionbank.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PublishRequest(@NotEmpty @Size(max = 200) List<@NotNull UUID> questionIds) {
}
