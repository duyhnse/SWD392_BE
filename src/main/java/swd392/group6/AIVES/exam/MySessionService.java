package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.exam.ExamDtos.MySession;
import swd392.group6.AIVES.user.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A student's own lượt thi with derived stage and result status (15 §2.2). No question content, ever. */
@Service
@RequiredArgsConstructor
@Transactional
class MySessionService {

    private static final String SELECT = """
            select s.session_id, e.viva_exam_id, c.course_id, c.code, c.name, e.title, e.description, e.instructions,
                   e.location, e.language, s.status, s.cancel_reason, e.status as exam_status, e.window_start, e.window_end,
                   e.time_limit_per_student_sec, e.main_question_count, e.max_followups_per_question, s.started_at,
                   s.deadline_at, s.ended_at, e.results_released, g.evaluation_id, g.status as evaluation_status
            from exam_sessions s
            join viva_exams e on e.viva_exam_id = s.viva_exam_id
            join courses c on c.course_id = e.course_id
            left join grade_evaluations g on g.session_id = s.session_id
            where s.student_id = :student""";

    private final ExamStatusRefresher refresher;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    PageResponse<MySession> list(User student, Set<SessionStage> stages, int page, int size) {
        refresher.refreshDue();
        Instant now = clock.instant();
        List<MySession> all = jdbc.query(SELECT + " order by e.window_start desc, e.title",
                        new MapSqlParameterSource("student", student.getUserId()), (rs, i) -> map(rs, now)).stream()
                .filter(s -> stages == null || stages.isEmpty() || stages.contains(s.stage()))
                .toList();
        int p = Math.max(page, 0);
        int sz = Math.clamp(size, 1, 100);
        int from = (int) Math.min((long) p * sz, all.size());
        return new PageResponse<>(all.subList(from, Math.min(from + sz, all.size())), p, sz, all.size());
    }

    MySession get(User student, UUID sessionId) {
        refresher.refreshDue();
        Instant now = clock.instant();
        return jdbc.query(SELECT + " and s.session_id = :id",
                        new MapSqlParameterSource("student", student.getUserId()).addValue("id", sessionId),
                        (rs, i) -> map(rs, now)).stream().findFirst()
                // Somebody else's lượt thi does not exist for this student (09 §1).
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "Session not found"));
    }

    private static MySession map(ResultSet rs, Instant now) throws SQLException {
        String status = rs.getString("status");
        Instant start = ExamQueries.instant(rs.getTimestamp("window_start"));
        Instant end = ExamQueries.instant(rs.getTimestamp("window_end"));
        SessionStage stage = SessionStage.of(status, rs.getString("cancel_reason"),
                ExamStatus.valueOf(rs.getString("exam_status")), start, end, now);
        ResultStatus result = ResultStatus.of(status, rs.getString("evaluation_status"), rs.getBoolean("results_released"));
        Instant startedAt = ExamQueries.instant(rs.getTimestamp("started_at"));
        return new MySession(rs.getObject("session_id", UUID.class), rs.getObject("viva_exam_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("title"),
                rs.getString("description"), rs.getString("instructions"), rs.getString("location"),
                Language.valueOf(rs.getString("language")), stage, result, start, end,
                rs.getInt("time_limit_per_student_sec"), rs.getInt("main_question_count"),
                rs.getInt("max_followups_per_question"), startedAt, ExamQueries.instant(rs.getTimestamp("deadline_at")),
                ExamQueries.instant(rs.getTimestamp("ended_at")), 1, startedAt != null ? 1 : 0,
                result == ResultStatus.RELEASED ? rs.getObject("evaluation_id", UUID.class) : null);
    }
}
