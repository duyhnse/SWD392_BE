package swd392.group6.AIVES.exam.dto;

import swd392.group6.AIVES.exam.domain.ExamStatus;

import java.time.Instant;
import java.util.UUID;

public record ExamSummary(UUID id, long no, UUID courseId, UUID templateId, String templateTitle, String title, ExamStatus status,
                   Instant checkinOpensAt, Instant checkinClosesAt, int mainQuestionCount, int durationSec,
                   long studentCount, long checkedInCount, long completedCount, long confirmedCount,
                   boolean resultsReleased, UUID retakeOfVivaExamId, int version) {
}
