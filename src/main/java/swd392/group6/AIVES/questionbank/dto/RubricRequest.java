package swd392.group6.AIVES.questionbank.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RubricRequest(@NotBlank @Size(max = 150) String name, @Size(max = 1000) String description,
                            @NotNull @Valid List<CriterionRequest> criteria) {
}
