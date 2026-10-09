package swd392.group6.AIVES.grading.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamApi;
import swd392.group6.AIVES.exam.ExamApi.ExamInfo;
import swd392.group6.AIVES.exam.ExamApi.AttemptInfo;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.User;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lookups and course-level authorization shared by the grading services. Student names/codes and the attempts of
 * a buổi thi are read with plain SQL (read-only; the exam and user modules own those tables).
 */
@Component
@RequiredArgsConstructor
class GradingSupport {

    private final NamedParameterJdbcTemplate jdbc;
    private final ExamApi examApi;
    private final CourseAccessApi courseAccess;
    private final GradeEvaluationRepository evaluations;

    record Student(UUID id, String fullName, String studentCode) {
    }

    /** One lượt thi of a buổi thi with its student, ordered like the student list. */
    record AttemptRow(UUID attemptId, UUID studentId, String fullName, String studentCode, String status,
                      String cancelReason, Instant endedAt) {
    }

    /** An evaluation together with the lượt thi it grades. */
    record EvaluationContext(GradeEvaluation evaluation, AttemptInfo attempt) {
    }

    AttemptInfo requireAttempt(UUID attemptId) {
        return examApi.getAttempt(attemptId)
                .orElseThrow(() -> ApiException.notFound("ATTEMPT_NOT_FOUND", "Attempt not found"));
    }

    ExamInfo requireExam(UUID vivaExamId) {
        return examApi.getExam(vivaExamId)
                .orElseThrow(() -> ApiException.notFound("VIVA_EXAM_NOT_FOUND", "Viva exam not found"));
    }

    ExamInfo requireExamRead(UUID vivaExamId, User user) {
        ExamInfo exam = requireExam(vivaExamId);
        courseAccess.requireRead(exam.courseId(), user);
        return exam;
    }

    GradeEvaluation requireEvaluation(UUID evaluationId) {
        return evaluations.findById(evaluationId)
                .orElseThrow(() -> ApiException.notFound("EVALUATION_NOT_FOUND", "Evaluation not found"));
    }

    EvaluationContext evaluationForRead(UUID evaluationId, User user) {
        GradeEvaluation evaluation = requireEvaluation(evaluationId);
        AttemptInfo attempt = requireAttempt(evaluation.getAttemptId());
        courseAccess.requireRead(attempt.courseId(), user);
        return new EvaluationContext(evaluation, attempt);
    }

    EvaluationContext evaluationForWrite(UUID evaluationId, User user) {
        GradeEvaluation evaluation = requireEvaluation(evaluationId);
        AttemptInfo attempt = requireAttempt(evaluation.getAttemptId());
        courseAccess.requireWrite(attempt.courseId(), user);
        return new EvaluationContext(evaluation, attempt);
    }

    void requireCourseRead(UUID courseId, User user) {
        courseAccess.requireRead(courseId, user);
    }

    void requireCourseWrite(UUID courseId, User user) {
        courseAccess.requireWrite(courseId, user);
    }

    Map<UUID, Student> students(Collection<UUID> userIds) {
        Map<UUID, Student> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        jdbc.query("select user_id, full_name, student_code from users where user_id in (:ids)",
                new MapSqlParameterSource("ids", userIds),
                rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    result.put(id, new Student(id, rs.getString(2), rs.getString(3)));
                });
        return result;
    }

    Student student(UUID userId) {
        return students(List.of(userId)).getOrDefault(userId, new Student(userId, null, null));
    }

    /** Lượt thi of a buổi thi, in student-list order. */
    List<AttemptRow> attemptsOfExam(UUID vivaExamId) {
        return jdbc.query("""
                        select s.attempt_id, s.student_id, u.full_name, u.student_code, s.status, s.cancel_reason, s.ended_at
                        from exam_attempts s
                        join users u on u.user_id = s.student_id
                        left join viva_exam_students vs on vs.viva_exam_id = s.viva_exam_id and vs.student_id = s.student_id
                        where s.viva_exam_id = :exam
                        order by vs.seq_no nulls last, u.student_code nulls last, u.full_name""",
                new MapSqlParameterSource("exam", vivaExamId),
                (rs, i) -> new AttemptRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), instant(rs.getTimestamp(7))));
    }

    /** A roster student of a buổi thi with their attempt, if they checked in (D48). */
    record RosterRow(UUID studentId, String fullName, String studentCode, UUID attemptId, String attemptStatus,
                     Instant endedAt) {
    }

    List<RosterRow> rosterOfExam(UUID vivaExamId) {
        return jdbc.query("""
                        select u.user_id, u.full_name, u.student_code, a.attempt_id, a.status, a.ended_at
                        from viva_exam_students vs
                        join users u on u.user_id = vs.student_id
                        left join exam_attempts a on a.viva_exam_id = vs.viva_exam_id and a.student_id = vs.student_id
                        where vs.viva_exam_id = :exam
                        order by vs.seq_no, u.student_code nulls last""",
                new MapSqlParameterSource("exam", vivaExamId),
                (rs, i) -> new RosterRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), instant(rs.getTimestamp(6))));
    }

    /** Ids of the lượt thi of a student, with their buổi thi. */
    Map<UUID, UUID> attemptExamIdsOfStudent(UUID studentId) {
        Map<UUID, UUID> result = new HashMap<>();
        jdbc.query("select attempt_id, viva_exam_id from exam_attempts where student_id = :s",
                new MapSqlParameterSource("s", studentId),
                rs -> {
                    result.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class));
                });
        return result;
    }

    NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
