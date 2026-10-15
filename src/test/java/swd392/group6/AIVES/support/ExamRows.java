package swd392.group6.AIVES.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Raw rows of the exam model v2 (D45–D50) for tests of other modules: đề thi, buổi thi, lượt thi and drawn
 * questions with their check-in snapshot. Bypasses the exam services on purpose.
 */
public final class ExamRows {

    private ExamRows() {
    }

    /** A template with one "any topic, any Bloom" row of {@code questions} × {@code secondsPerQuestion}. */
    public static UUID template(JdbcTemplate jdbc, UUID courseId, UUID createdBy, int questions, int secondsPerQuestion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        insert into exam_templates (exam_template_id, course_id, title, language, created_by, is_locked)
                        values (?, ?, ?, 'VI', ?, true)""", id, courseId, "Template " + id.toString().substring(0, 8), createdBy);
        jdbc.update("""
                        insert into exam_template_items (template_item_id, exam_template_id, question_count, seconds_per_question)
                        values (?, ?, ?, ?)""", UUID.randomUUID(), id, questions, secondsPerQuestion);
        return id;
    }

    public static UUID exam(JdbcTemplate jdbc, UUID courseId, UUID lecturerId, int questions, Instant opens, Instant closes,
                            String status) {
        UUID template = template(jdbc, courseId, lecturerId, questions, 180);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        insert into viva_exams (viva_exam_id, course_id, exam_template_id, title, created_by, examiner_id,
                                                checkin_opens_at, checkin_closes_at, status)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, courseId, template, "Viva " + id.toString().substring(0, 8), lecturerId, lecturerId,
                Timestamp.from(opens), Timestamp.from(closes), status);
        return id;
    }

    public static void roster(JdbcTemplate jdbc, UUID examId, UUID studentId) {
        jdbc.update("""
                        insert into viva_exam_students (viva_exam_id, student_id, seq_no)
                        values (?, ?, (select coalesce(max(seq_no), 0) + 1 from viva_exam_students where viva_exam_id = ?))
                        on conflict do nothing""", examId, studentId, examId);
    }

    /** Roster entry + attempt. {@code CANCELLED} attempts get a cancel reason (DB rule). */
    public static UUID attempt(JdbcTemplate jdbc, UUID examId, UUID studentId, String status, Instant startedAt,
                               Instant endedAt) {
        roster(jdbc, examId, studentId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        insert into exam_attempts (attempt_id, viva_exam_id, course_id, student_id, examiner_id, status,
                                                   end_reason, cancel_reason, started_at, deadline_at, ended_at, consent_recorded_at)
                        select ?, e.viva_exam_id, e.course_id, ?, e.examiner_id, ?, ?, ?, ?, ?, ?, ?
                        from viva_exams e where e.viva_exam_id = ?""",
                id, studentId, status, "COMPLETED".equals(status) ? "ALL_QUESTIONS_DONE" : null,
                "CANCELLED".equals(status) ? "Voided in a test" : null, Timestamp.from(startedAt),
                Timestamp.from(startedAt.plusSeconds(900)), endedAt == null ? null : Timestamp.from(endedAt),
                Timestamp.from(startedAt), examId);
        return id;
    }

    /** A drawn question with the snapshot of the bank question as it is now (D49). */
    public static UUID attemptQuestion(JdbcTemplate jdbc, UUID attemptId, UUID questionId, int orderNo, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        insert into attempt_questions (attempt_question_id, attempt_id, question_id, order_no, status, topic_id,
                                                       topic_name, bloom_level, language, content,
                                                       reference_answer, question_version, rubric_snapshot, time_budget_sec)
                        select ?, ?, q.question_id, ?, ?, c.topic_id, c.name, q.bloom_level, q.language,
                               q.content, q.reference_answer, q.version,
                               coalesce((select jsonb_build_object('rubricId', r.rubric_id, 'name', r.name, 'criteria',
                                          coalesce((select jsonb_agg(jsonb_build_object('criterionId', rc.criterion_id,
                                                      'name', rc.name, 'description', rc.description,
                                                      'maxScore', rc.max_score, 'weightPercent', rc.weight_percent,
                                                      'sortOrder', rc.sort_order) order by rc.sort_order)
                                                    from rubric_criteria rc where rc.rubric_id = r.rubric_id), '[]'::jsonb))
                                         from rubrics r where r.rubric_id = q.rubric_id), '{"criteria": []}'::jsonb),
                               180
                        from questions q join topics c on c.topic_id = q.topic_id where q.question_id = ?""",
                id, attemptId, orderNo, status, questionId);
        return id;
    }
}
