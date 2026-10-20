package swd392.group6.AIVES.questionbank.dto;

import java.util.UUID;

public record SourceDto(UUID id, UUID materialId, String materialName, UUID chunkId, String locationLabel,
                        String excerpt) {
}
