package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;

/**
 * Time-driven transitions of buổi thi (BR-E4, 15 §2.1, D47): READY → OPEN once check-in opened, READY/OPEN → CLOSED
 * once it closed. Students who never checked in are MISSED by derivation; running attempts are not touched.
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
        int closed = jdbc.update("""
                update viva_exams set status = 'CLOSED', version = version + 1, updated_at = ?
                where status in ('READY', 'OPEN') and checkin_closes_at < ?""", now, now);
        int opened = jdbc.update("""
                update viva_exams set status = 'OPEN', version = version + 1, updated_at = ?
                where status = 'READY' and checkin_opens_at <= ? and checkin_closes_at >= ?""", now, now, now);
        if (closed > 0 || opened > 0) {
            log.info("Exam check-in refresh: {} opened, {} closed", opened, closed);
        }
    }
}
