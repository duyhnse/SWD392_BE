package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only SQL implementation of {@link ExamApi} so other modules can be built in parallel.
 * The exam module owner may replace it (keep exactly one bean).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcExamApi implements ExamApi {

    private final JdbcTemplate jdbc;

    @Override
    public Optional<ExamInfo> getExam(UUID vivaExamId) {
        return jdbc.query("""
                        select viva_exam_id, course_id, title, status, window_start, window_end, time_limit_per_student_sec,
                               main_question_count, max_followups_per_question, examiner_id, results_released, results_released_at
                        from viva_exams where viva_exam_id = ?""",
                (rs, i) -> new ExamInfo(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), instant(rs.getTimestamp(5)), instant(rs.getTimestamp(6)), rs.getInt(7), rs.getInt(8),
                        rs.getInt(9), rs.getObject(10, UUID.class), rs.getBoolean(11), instant(rs.getTimestamp(12))),
                vivaExamId).stream().findFirst();
    }

    @Override
    public Optional<SessionInfo> getSession(UUID sessionId) {
        return jdbc.query("""
                        select session_id, viva_exam_id, course_id, student_id, examiner_id, status, end_reason, cancel_reason,
                               started_at, deadline_at, ended_at
                        from exam_sessions where session_id = ?""",
                (rs, i) -> new SessionInfo(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                        rs.getString(8), instant(rs.getTimestamp(9)), instant(rs.getTimestamp(10)), instant(rs.getTimestamp(11))),
                sessionId).stream().findFirst();
    }

    @Override
    public List<SessionQuestionInfo> getSessionQuestions(UUID sessionId) {
        return jdbc.query("""
                        select session_question_id, question_id, order_no, status from session_questions
                        where session_id = ? order by order_no""",
                (rs, i) -> new SessionQuestionInfo(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3),
                        rs.getString(4)), sessionId);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
