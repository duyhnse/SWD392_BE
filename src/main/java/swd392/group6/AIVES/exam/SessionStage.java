package swd392.group6.AIVES.exam;

import java.time.Instant;

/** What a student sees for a lượt thi — derived, never stored (15 §2.2, D31). */
enum SessionStage {
    UPCOMING,
    AVAILABLE,
    IN_PROGRESS,
    INTERRUPTED,
    COMPLETED,
    MISSED,
    CANCELLED;

    /**
     * @param sessionStatus {@code exam_sessions.status}
     * @param cancelReason  {@code exam_sessions.cancel_reason}
     */
    static SessionStage of(String sessionStatus, String cancelReason, ExamStatus examStatus,
                           Instant windowStart, Instant windowEnd, Instant now) {
        return switch (sessionStatus) {
            case "IN_PROGRESS" -> IN_PROGRESS;
            case "INTERRUPTED" -> INTERRUPTED;
            case "COMPLETED" -> COMPLETED;
            case "CANCELLED" -> "NO_SHOW".equals(cancelReason) ? MISSED : CANCELLED;
            default -> scheduled(examStatus, windowStart, windowEnd, now);
        };
    }

    private static SessionStage scheduled(ExamStatus examStatus, Instant windowStart, Instant windowEnd, Instant now) {
        if (examStatus == ExamStatus.CANCELLED) {
            return CANCELLED;
        }
        // The exam status is refreshed lazily before reads; the window checks only guard against a stale row.
        if (examStatus == ExamStatus.CLOSED || now.isAfter(windowEnd)) {
            return MISSED;
        }
        if (examStatus == ExamStatus.OPEN && !now.isBefore(windowStart)) {
            return AVAILABLE;
        }
        return UPCOMING;
    }
}
