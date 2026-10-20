package swd392.group6.AIVES.questionbank.dto;

import java.util.List;
import java.util.UUID;

public record PublishResult(List<UUID> published, List<PublishFailure> failed) {
}
