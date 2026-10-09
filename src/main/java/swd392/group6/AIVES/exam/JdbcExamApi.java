package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only SQL implementation of {@link ExamApi}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcExamApi implements ExamApi {

    private static final String ATTEMPT_SELECT = """
            select a.attempt_id, a.viva_exam_id, a.course_id, a.student_id, a.examiner_id, a.status, a.end_reason,
                   a.started_at, a.deadline_at, a.ended_at, a.disconnect_count, a.frozen_sec_total, v.seq_no
            from exam_attempts a
            left join viva_exam_students v on v.viva_exam_id = a.viva_exam_id and v.student_id = a.student_id""";

    private static final RowMapper<AttemptInfo> ATTEMPT = (rs, i) -> new AttemptInfo(rs.getObject(1, UUID.class),
            rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
            rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7), instant(rs.getTimestamp(8)),
            instant(rs.getTimestamp(9)), instant(rs.getTimestamp(10)), rs.getInt(11), rs.getInt(12),
            (Integer) rs.getObject(13));

    private final JdbcTemplate jdbc;

    @Override
    public Optional<ExamInfo> getExam(UUID vivaExamId) {
        return jdbc.query("""
                        select e.viva_exam_id, e.course_id, e.exam_template_id, e.title, e.status, e.checkin_opens_at,
                               e.checkin_closes_at,
                               (select coalesce(sum(i.question_count), 0) from exam_template_items i
                                  where i.exam_template_id = t.exam_template_id),
                               t.max_followups_per_question, t.max_answer_sec, t.silence_warning_sec, t.show_question_text,
                               t.language, t.pass_score, e.examiner_id, e.results_released, e.results_released_at,
                               e.reconnect_grace_sec, e.max_disconnects, e.max_frozen_sec, e.replace_main_after_sec
                        from viva_exams e join exam_templates t on t.exam_template_id = e.exam_template_id
                        where e.viva_exam_id = ?""",
                (rs, i) -> new ExamInfo(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), instant(rs.getTimestamp(6)),
                        instant(rs.getTimestamp(7)), rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getInt(11),
                        rs.getBoolean(12), rs.getString(13), rs.getBigDecimal(14), rs.getObject(15, UUID.class),
                        rs.getBoolean(16), instant(rs.getTimestamp(17)), rs.getInt(18), rs.getInt(19), rs.getInt(20),
                        rs.getInt(21)),
                vivaExamId).stream().findFirst();
    }

    @Override
    public Optional<AttemptInfo> getAttempt(UUID attemptId) {
        return jdbc.query(ATTEMPT_SELECT + " where a.attempt_id = ?", ATTEMPT, attemptId).stream().findFirst();
    }

    @Override
    public List<AttemptInfo> listAttempts(UUID vivaExamId) {
        return jdbc.query(ATTEMPT_SELECT + " where a.viva_exam_id = ? order by v.seq_no nulls last, a.started_at",
                ATTEMPT, vivaExamId);
    }

    @Override
    public List<AttemptQuestionInfo> getAttemptQuestions(UUID attemptId) {
        return jdbc.query("""
                        select attempt_question_id, question_id, order_no, status, chapter_no, chapter_title, bloom_level,
                               language, content, reference_answer, rubric_snapshot::text, time_budget_sec, time_used_sec,
                               void_reason
                        from attempt_questions where attempt_id = ? order by order_no, status = 'VOIDED' desc""",
                (rs, i) -> new AttemptQuestionInfo(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3),
                        rs.getString(4), (Integer) rs.getObject(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getString(11), rs.getInt(12), rs.getInt(13),
                        rs.getString(14)), attemptId);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
