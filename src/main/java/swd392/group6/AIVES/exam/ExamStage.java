package swd392.group6.AIVES.exam;

import java.time.Instant;

/** Where one roster student stands in a buổi thi — derived, never stored (15 §2.2, D31, D48). */
enum ExamStage {
    /** Published, check-in not open yet. */
    UPCOMING,
    /** Check-in window open, not checked in. */
    AVAILABLE,
    IN_PROGRESS,
    INTERRUPTED,
    COMPLETED,
    /** Check-in closed without an attempt. */
    MISSED,
    /** The buổi thi was cancelled, or the lecturer voided the attempt. */
    CANCELLED;

    /**
     * @param attemptStatus {@code exam_attempts.status}, null when the student has not checked in
     */
    static ExamStage of(String attemptStatus, ExamStatus examStatus, Instant opensAt, Instant closesAt, Instant now) {
        if (attemptStatus != null) {
            return switch (attemptStatus) {
                case "IN_PROGRESS" -> IN_PROGRESS;
                case "INTERRUPTED" -> INTERRUPTED;
                case "COMPLETED" -> COMPLETED;
                default -> CANCELLED;
            };
        }
        if (examStatus == ExamStatus.CANCELLED) {
            return CANCELLED;
        }
        // The exam status is refreshed lazily before reads; the window checks only guard against a stale row.
        if (examStatus == ExamStatus.CLOSED || now.isAfter(closesAt)) {
            return MISSED;
        }
        if (examStatus == ExamStatus.OPEN && !now.isBefore(opensAt)) {
            return AVAILABLE;
        }
        return UPCOMING;
    }
}
