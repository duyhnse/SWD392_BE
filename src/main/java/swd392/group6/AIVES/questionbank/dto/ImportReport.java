package swd392.group6.AIVES.questionbank.dto;

import java.util.List;
import java.util.UUID;

public record ImportReport(int total, int imported, List<UUID> questionIds, List<ImportRowError> errors) {
}
