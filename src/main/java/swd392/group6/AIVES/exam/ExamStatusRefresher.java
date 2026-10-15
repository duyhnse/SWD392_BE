package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Time-driven transitions of buổi thi (BR-E4, BR-E5, 15 §2.1): READY → OPEN once the window started,
 * READY/OPEN → CLOSED once it ended (SCHEDULED lượt thi → CANCELLED / NO_SHOW).
 * Run lazily before reads and by {@link ExamScheduler} every minute; idempotent and safe to run concurrently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ExamStatusRefresher {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional
    public void refreshDue() {
        Timestamp now = Timestamp.from(clock.instant());
        List<UUID> closed = jdbc.queryForList("""
                update viva_exams set status = 'CLOSED', version = version + 1, updated_at = ?
                where status in ('READY', 'OPEN') and window_end < ? returning viva_exam_id""", UUID.class, now, now);
        for (UUID examId : closed) {
            markNoShows(examId);
        }
        int opened = jdbc.update("""
                update viva_exams set status = 'OPEN', version = version + 1, updated_at = ?
                where status = 'READY' and window_start <= ? and window_end >= ?""", now, now, now);
        if (!closed.isEmpty() || opened > 0) {
            log.info("Exam window refresh: {} opened, {} closed", opened, closed.size());
        }
    }

    /** Every lượt thi that never started becomes a no-show; started ones continue to their own deadline. */
    int markNoShows(UUID examId) {
        return jdbc.update("""
                update exam_sessions set status = 'CANCELLED', cancel_reason = 'NO_SHOW'
                where viva_exam_id = ? and status = 'SCHEDULED'""", examId);
    }
}
