package swd392.group6.AIVES.grading.dto;

import java.time.Instant;
import java.util.UUID;

public record TurnDto(UUID turnId, String type, int followupIndex, String questionText, String transcript, String status,
               String audioUrl, Integer durationSec, Instant askedAt, String decision) {
}
