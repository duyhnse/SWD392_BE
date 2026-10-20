package swd392.group6.AIVES.user.dto;

import java.util.UUID;

public record CourseRef(UUID courseId, String code, String name, boolean active) {
}
