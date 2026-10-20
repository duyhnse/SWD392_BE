package swd392.group6.AIVES.user.dto;

import java.util.UUID;

public record LecturerSummary(UUID userId, String username, String fullName, String email, boolean active,
                       String avatarUrl, long courseCount) {
}
