package swd392.group6.AIVES.exam.dto;

import swd392.group6.AIVES.exam.domain.ExamStage;
import swd392.group6.AIVES.exam.domain.ResultStatus;

import swd392.group6.AIVES.common.Language;
import java.time.Instant;
import java.util.UUID;

/**
 * A buổi thi as the student sees it (15 §2.2) — never with question content. {@code attemptId} is set once
 * the student checked in.
 */
public record MyExam(UUID vivaExamId, long examNo, UUID courseId, String courseCode, String courseName, String title,
              String description, String instructions, String location, Language language, ExamStage stage,
              ResultStatus resultStatus, Instant checkinOpensAt, Instant checkinClosesAt, int durationSec,
              int mainQuestionCount, int maxFollowupsPerQuestion, boolean showQuestionText, UUID attemptId,
              Long attemptNo, Instant startedAt, Instant deadlineAt, Instant endedAt, int attemptsAllowed, int attemptsUsed,
              UUID evaluationId) {
}
