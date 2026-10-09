package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamDtos.AttemptDetail;
import swd392.group6.AIVES.exam.ExamDtos.AttemptQuestionView;
import swd392.group6.AIVES.questionbank.BloomLevel;
import swd392.group6.AIVES.user.User;

import java.util.List;
import java.util.UUID;

/** Lecturer view of one lượt thi: timing, connection counters and the drawn questions with their snapshot (D49, D50). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AttemptService {

    private final ExamAccess access;
    private final NamedParameterJdbcTemplate jdbc;

    AttemptDetail get(UUID attemptId, User user) {
        MapSqlParameterSource p = new MapSqlParameterSource("a", attemptId);
        List<UUID> examId = jdbc.queryForList("select viva_exam_id from exam_attempts where attempt_id = :a", p, UUID.class);
        if (examId.isEmpty()) {
            throw notFound();
        }
        try {
            access.read(examId.getFirst(), user);
        } catch (ApiException e) {
            throw notFound();
        }
        List<AttemptQuestionView> questions = jdbc.query("""
                        select attempt_question_id, question_id, order_no, status, chapter_no, chapter_title, bloom_level,
                               content, reference_answer, time_budget_sec, time_used_sec, rubric_snapshot->>'name' as rubric_name,
                               started_at, ended_at, replaces_attempt_question_id, void_reason
                        from attempt_questions where attempt_id = :a order by order_no, status = 'VOIDED' desc""", p,
                (rs, i) -> new AttemptQuestionView(rs.getObject("attempt_question_id", UUID.class),
                        rs.getObject("question_id", UUID.class), rs.getInt("order_no"), rs.getString("status"),
                        (Integer) rs.getObject("chapter_no"), rs.getString("chapter_title"),
                        rs.getString("bloom_level") == null ? null : BloomLevel.valueOf(rs.getString("bloom_level")),
                        rs.getString("content"), rs.getString("reference_answer"), rs.getInt("time_budget_sec"),
                        rs.getInt("time_used_sec"), rs.getString("rubric_name"),
                        ExamQueries.instant(rs.getTimestamp("started_at")), ExamQueries.instant(rs.getTimestamp("ended_at")),
                        rs.getObject("replaces_attempt_question_id", UUID.class), rs.getString("void_reason")));
        return jdbc.query("""
                        select a.attempt_id, a.viva_exam_id, a.course_id, a.student_id, u.username, u.full_name, u.student_code,
                               a.status, a.end_reason, a.cancel_reason, a.started_at, a.deadline_at, a.ended_at,
                               a.consent_recorded_at, a.disconnect_count, a.frozen_sec_total, a.client_info, a.selection_seed
                        from exam_attempts a join users u on u.user_id = a.student_id where a.attempt_id = :a""", p,
                (rs, i) -> new AttemptDetail(rs.getObject("attempt_id", UUID.class), rs.getObject("viva_exam_id", UUID.class),
                        rs.getObject("course_id", UUID.class), rs.getObject("student_id", UUID.class),
                        rs.getString("username"), rs.getString("full_name"), rs.getString("student_code"),
                        rs.getString("status"), rs.getString("end_reason"), rs.getString("cancel_reason"),
                        ExamQueries.instant(rs.getTimestamp("started_at")), ExamQueries.instant(rs.getTimestamp("deadline_at")),
                        ExamQueries.instant(rs.getTimestamp("ended_at")),
                        ExamQueries.instant(rs.getTimestamp("consent_recorded_at")), rs.getInt("disconnect_count"),
                        rs.getInt("frozen_sec_total"), rs.getString("client_info"), (Long) rs.getObject("selection_seed"),
                        questions)).getFirst();
    }

    static ApiException notFound() {
        return ApiException.notFound("ATTEMPT_NOT_FOUND", "Attempt not found");
    }
}
