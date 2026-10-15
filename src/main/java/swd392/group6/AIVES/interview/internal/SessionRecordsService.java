package swd392.group6.AIVES.interview.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamApi;
import swd392.group6.AIVES.exam.ExamApi.SessionInfo;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.storage.StoredObject;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read side of a lượt thi record (15 §5.4): turns, audit events and audio. Lecturers assigned to the course
 * (ADMIN read-only) see everything; the session's student sees their own turns only once the session is
 * COMPLETED and the exam's results are released.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class SessionRecordsService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;
    private final ExamApi examApi;
    private final CourseAccessApi courseAccess;
    private final StoragePort storage;

    public List<SessionRecordDtos.TurnDto> turns(UUID sessionId, User user) {
        SessionInfo session = requireSession(sessionId);
        boolean lecturerView = user.getRole() != Role.STUDENT;
        if (lecturerView) {
            courseAccess.requireRead(session.courseId(), user);
        } else {
            requireOwnReleased(session, user);
        }
        return jdbc.query("""
                select t.turn_id, t.session_question_id, sq.order_no, t.turn_order, t.turn_type, t.followup_index,
                       t.question_text, t.language, t.status, t.asked_at, t.answer_submitted_at, t.response_duration_sec,
                       t.student_transcript, t.followup_decision, t.ai_analysis, t.processing_ms,
                       t.question_audio_key, t.answer_audio_key
                from exam_turns t join session_questions sq on sq.session_question_id = t.session_question_id
                where t.session_id = ? order by t.turn_order""",
                (rs, i) -> toTurn(rs, lecturerView), sessionId);
    }

    public List<SessionRecordDtos.EventDto> events(UUID sessionId, User user) {
        SessionInfo session = requireSession(sessionId);
        courseAccess.requireRead(session.courseId(), user);
        return jdbc.query("""
                select event_id, turn_id, type, actor_id, payload, created_at from session_events
                where session_id = ? order by created_at, event_id""",
                (rs, i) -> new SessionRecordDtos.EventDto(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getObject(4, UUID.class), parse(rs.getString(5)), instant(rs.getTimestamp(6))),
                sessionId);
    }

    /** Answer audio: lecturers, or the student under the same rule as {@link #turns}. */
    public StoredObject answerAudio(UUID turnId, User user) {
        TurnRef turn = requireTurn(turnId);
        SessionInfo session = requireSession(turn.sessionId());
        if (user.getRole() == Role.STUDENT) {
            requireOwnReleased(session, user);
        } else {
            courseAccess.requireRead(session.courseId(), user);
        }
        return load(turn.answerAudioKey());
    }

    /** Question audio: lecturers, or the session's own student at any time (it is played during the exam). */
    public StoredObject questionAudio(UUID turnId, User user) {
        TurnRef turn = requireTurn(turnId);
        SessionInfo session = requireSession(turn.sessionId());
        if (user.getRole() == Role.STUDENT) {
            if (!session.studentId().equals(user.getUserId())) {
                throw turnNotFound();
            }
        } else {
            courseAccess.requireRead(session.courseId(), user);
        }
        return load(turn.questionAudioKey());
    }

    private SessionInfo requireSession(UUID sessionId) {
        return examApi.getSession(sessionId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "Session not found"));
    }

    private void requireOwnReleased(SessionInfo session, User user) {
        if (!session.studentId().equals(user.getUserId())) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "Session not found");
        }
        boolean released = examApi.getExam(session.vivaExamId()).map(ExamApi.ExamInfo::resultsReleased).orElse(false);
        if (!"COMPLETED".equals(session.status()) || !released) {
            throw ApiException.forbidden("RESULTS_NOT_RELEASED", "The record of this session is not available yet");
        }
    }

    private TurnRef requireTurn(UUID turnId) {
        return jdbc.query("select session_id, question_audio_key, answer_audio_key from exam_turns where turn_id = ?",
                        (rs, i) -> new TurnRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), turnId)
                .stream().findFirst().orElseThrow(SessionRecordsService::turnNotFound);
    }

    private StoredObject load(String key) {
        if (key == null || key.isBlank()) {
            throw audioNotFound();
        }
        try {
            return storage.get(key).orElseThrow(SessionRecordsService::audioNotFound);
        } catch (IllegalArgumentException invalidKey) {
            throw audioNotFound();
        }
    }

    private static SessionRecordDtos.TurnDto toTurn(ResultSet rs, boolean lecturerView) throws SQLException {
        UUID turnId = rs.getObject("turn_id", UUID.class);
        String questionAudioKey = rs.getString("question_audio_key");
        String answerAudioKey = rs.getString("answer_audio_key");
        return new SessionRecordDtos.TurnDto(turnId, rs.getObject("session_question_id", UUID.class),
                rs.getInt("order_no"), rs.getInt("turn_order"), rs.getString("turn_type"), rs.getInt("followup_index"),
                rs.getString("question_text"), rs.getString("language"), rs.getString("status"),
                instant(rs.getTimestamp("asked_at")), instant(rs.getTimestamp("answer_submitted_at")),
                (Integer) rs.getObject("response_duration_sec"), rs.getString("student_transcript"),
                lecturerView ? rs.getString("followup_decision") : null,
                lecturerView ? parse(rs.getString("ai_analysis")) : null,
                lecturerView ? (Integer) rs.getObject("processing_ms") : null,
                questionAudioKey == null ? null : "/api/v1/turns/" + turnId + "/question-audio",
                answerAudioKey == null ? null : "/api/v1/turns/" + turnId + "/answer-audio");
    }

    private static JsonNode parse(String json) {
        return json == null ? null : JSON.readTree(json);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static ApiException turnNotFound() {
        return ApiException.notFound("TURN_NOT_FOUND", "Turn not found");
    }

    private static ApiException audioNotFound() {
        return ApiException.notFound("AUDIO_NOT_FOUND", "No audio recorded for this turn");
    }

    private record TurnRef(UUID sessionId, String questionAudioKey, String answerAudioKey) {
    }
}
