package swd392.group6.AIVES.questionbank.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TermsDto(@NotNull List<String> terms) {
}
