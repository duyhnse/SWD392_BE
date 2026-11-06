package swd392.group6.AIVES.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TemplateItemsRequest(@NotNull List<@Valid @NotNull TemplateItemRequest> items) {
}
