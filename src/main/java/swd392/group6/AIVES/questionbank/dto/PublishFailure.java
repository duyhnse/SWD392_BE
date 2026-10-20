package swd392.group6.AIVES.questionbank.dto;

import java.util.List;
import java.util.UUID;

public record PublishFailure(UUID id, List<String> errors) {
}
