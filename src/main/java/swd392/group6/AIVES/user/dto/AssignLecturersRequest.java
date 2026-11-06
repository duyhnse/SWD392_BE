package swd392.group6.AIVES.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record AssignLecturersRequest(@NotNull(message = "lecturerIds is required") List<UUID> lecturerIds) {
}
