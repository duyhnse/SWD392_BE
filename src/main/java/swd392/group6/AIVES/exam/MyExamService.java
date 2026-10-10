package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.exam.ExamDtos.MyExam;
import swd392.group6.AIVES.user.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A student's buổi thi with derived stage and result status (15 §2.2). No question content, ever. */
@Service
@RequiredArgsConstructor
@Transactional
class MyExamService {

    private static final String SELECT = """
            select e.viva_exam_id, e.display_no as exam_no, c.course_id, c.code, c.name, e.title, e.description, e.instructions, e.location,
                   t.language, t.max_followups_per_question, t.show_question_text, e.status as exam_status,
                   e.checkin_opens_at, e.checkin_closes_at, e.results_released,
                   (select coalesce(sum(i.question_count), 0) from exam_template_items i
                      where i.exam_template_id = t.exam_template_id) as questions,
                   (select coalesce(sum(i.question_count * i.seconds_per_question), 0) from exam_template_items i
                      where i.exam_template_id = t.exam_template_id) as duration,
                   a.attempt_id, a.display_no as attempt_no, a.status, a.started_at, a.deadline_at, a.ended_at,
                   g.evaluation_id, g.status as evaluation_status
            from viva_exam_students v
            join viva_exams e on e.viva_exam_id = v.viva_exam_id
            join exam_templates t on t.exam_template_id = e.exam_template_id
            join courses c on c.course_id = e.course_id
            left join exam_attempts a on a.viva_exam_id = e.viva_exam_id and a.student_id = v.student_id
            left join grade_evaluations g on g.attempt_id = a.attempt_id
            where v.student_id = :student and e.status <> 'DRAFT'""";

    private final ExamStatusRefresher refresher;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    PageResponse<MyExam> list(User student, Set<ExamStage> stages, int page, int size) {
        refresher.refreshDue();
        Instant now = clock.instant();
        List<MyExam> all = jdbc.query(SELECT + " order by e.checkin_opens_at desc, e.title",
                        new MapSqlParameterSource("student", student.getUserId()), (rs, i) -> map(rs, now)).stream()
                .filter(s -> stages == null || stages.isEmpty() || stages.contains(s.stage()))
                .toList();
        int p = Math.max(page, 0);
        int sz = Math.clamp(size, 1, 100);
        int from = (int) Math.min((long) p * sz, all.size());
        return new PageResponse<>(all.subList(from, Math.min(from + sz, all.size())), p, sz, all.size());
    }

    MyExam get(User student, UUID examId) {
        refresher.refreshDue();
        Instant now = clock.instant();
        return jdbc.query(SELECT + " and e.viva_exam_id = :id",
                        new MapSqlParameterSource("student", student.getUserId()).addValue("id", examId),
                        (rs, i) -> map(rs, now)).stream().findFirst()
                // A buổi thi the student is not on does not exist for them (09 §1).
                .orElseThrow(() -> ApiException.notFound("VIVA_EXAM_NOT_FOUND", "Viva exam not found"));
    }

    private static MyExam map(ResultSet rs, Instant now) throws SQLException {
        String status = rs.getString("status");
        Instant opens = ExamQueries.instant(rs.getTimestamp("checkin_opens_at"));
        Instant closes = ExamQueries.instant(rs.getTimestamp("checkin_closes_at"));
        ExamStage stage = ExamStage.of(status, ExamStatus.valueOf(rs.getString("exam_status")), opens, closes, now);
        ResultStatus result = ResultStatus.of(status, rs.getString("evaluation_status"), rs.getBoolean("results_released"));
        UUID attemptId = rs.getObject("attempt_id", UUID.class);
        return new MyExam(rs.getObject("viva_exam_id", UUID.class), rs.getLong("exam_no"), rs.getObject("course_id", UUID.class),
                rs.getString("code"), rs.getString("name"), rs.getString("title"), rs.getString("description"),
                rs.getString("instructions"), rs.getString("location"), Language.valueOf(rs.getString("language")),
                stage, result, opens, closes, rs.getInt("duration"), rs.getInt("questions"),
                rs.getInt("max_followups_per_question"), rs.getBoolean("show_question_text"), attemptId, (Long) rs.getObject("attempt_no"),
                ExamQueries.instant(rs.getTimestamp("started_at")), ExamQueries.instant(rs.getTimestamp("deadline_at")),
                ExamQueries.instant(rs.getTimestamp("ended_at")), 1, attemptId != null ? 1 : 0,
                result == ResultStatus.RELEASED ? rs.getObject("evaluation_id", UUID.class) : null);
    }
}
