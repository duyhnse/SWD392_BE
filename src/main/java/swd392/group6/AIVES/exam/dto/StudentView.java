package swd392.group6.AIVES.exam.dto;

import swd392.group6.AIVES.exam.domain.ExamStage;

import java.time.Instant;
import java.util.UUID;

public record StudentView(UUID studentId, String username, String fullName, String studentCode, int seqNo,
                   Instant addedAt, UUID attemptId, String attemptStatus, ExamStage stage) {
}
