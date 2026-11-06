package swd392.group6.AIVES.questionbank.dto;

import java.util.UUID;

/** Moves a material to another chapter; {@code null} = no chapter. */
public record MaterialTopicRequest(UUID topicId) {
}
