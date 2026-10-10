package swd392.group6.AIVES.grading;

import org.springframework.jdbc.core.JdbcTemplate;
import swd392.group6.AIVES.support.ExamRows;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Inserts course content, a buổi thi and completed lượt thi straight into the database (the exam runtime is not
 * part of this milestone). Users come from {@link TestUsers}.
 */
public class ExamFixtures {

    private final JdbcTemplate jdbc;
    private final TestUsers users;

    public ExamFixtures(JdbcTemplate jdbc, TestUsers users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public record CourseFx(UUID courseId, User lecturer, UUID topicId) {
    }

    public record RubricFx(UUID rubricId, List<UUID> criterionIds) {
    }

    public record SessionFx(UUID sessionId, User student, List<UUID> sessionQuestionIds) {
    }

    public static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public CourseFx course() {
        UUID courseId = UUID.randomUUID();
        jdbc.update("insert into courses (course_id, code, name) values (?, ?, ?)", courseId, "C" + unique(), "Course");
        User lecturer = users.create(Role.LECTURER);
        assign(courseId, lecturer);
        UUID topicId = UUID.randomUUID();
        jdbc.update("insert into topics (topic_id, course_id, name, created_by) values (?, ?, ?, ?)",
                topicId, courseId, "Topic " + unique(), lecturer.getUserId());
        return new CourseFx(courseId, lecturer, topicId);
    }

    public void assign(UUID courseId, User lecturer) {
        jdbc.update("insert into course_lecturers (course_id, lecturer_id) values (?, ?)", courseId, lecturer.getUserId());
    }

    /** {@code criteria} = pairs of (maxScore, weightPercent). */
    public RubricFx rubric(CourseFx course, double... criteria) {
        UUID rubricId = UUID.randomUUID();
        jdbc.update("insert into rubrics (rubric_id, course_id, name, created_by) values (?, ?, ?, ?)",
                rubricId, course.courseId(), "Rubric " + unique(), course.lecturer().getUserId());
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < criteria.length; i += 2) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                            insert into rubric_criteria (criterion_id, rubric_id, name, description, max_score, weight_percent, sort_order)
                            values (?, ?, ?, ?, ?, ?, ?)""",
                    id, rubricId, "Criterion " + (i / 2 + 1), "desc", BigDecimal.valueOf(criteria[i]),
                    BigDecimal.valueOf(criteria[i + 1]), i / 2);
            ids.add(id);
        }
        return new RubricFx(rubricId, ids);
    }

    public UUID question(CourseFx course, UUID rubricId, String content) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        insert into questions (question_id, course_id, topic_id, content, reference_answer, bloom_level, language,
                                               status, origin, rubric_id, owner_id)
                        values (?, ?, ?, ?, 'SECRET reference answer', 'UNDERSTAND', 'VI', 'PUBLISHED', 'MANUAL', ?, ?)""",
                id, course.courseId(), course.topicId(), content, rubricId, course.lecturer().getUserId());
        return id;
    }

    public UUID exam(CourseFx course, int mainQuestionCount) {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        return ExamRows.exam(jdbc, course.courseId(), course.lecturer().getUserId(), mainQuestionCount, start,
                start.plus(4, ChronoUnit.HOURS), "OPEN");
    }

    public User student(String fullName) {
        User student = users.create(Role.STUDENT);
        String code = "SE" + unique();
        jdbc.update("update users set student_code = ?, full_name = ? where user_id = ?", code, fullName, student.getUserId());
        student.setStudentCode(code);
        student.setFullName(fullName);
        return student;
    }

    /**
     * A lượt thi (attempt) with one drawn question per entry of {@code questionIds}; {@code threadStatuses} are the
     * attempt_questions statuses (DONE, SKIPPED, NOT_REACHED…). Status {@code NO_SHOW} = roster entry only: the
     * student never checked in (D48), {@code attemptId} is then null.
     */
    public SessionFx session(UUID examId, CourseFx course, User student, String status, String cancelReason,
                             List<UUID> questionIds, List<String> threadStatuses) {
        if ("NO_SHOW".equals(status) || "NO_SHOW".equals(cancelReason)) {
            ExamRows.roster(jdbc, examId, student.getUserId());
            return new SessionFx(null, student, List.of());
        }
        Instant started = Instant.now().minus(1, ChronoUnit.HOURS);
        UUID attemptId = ExamRows.attempt(jdbc, examId, student.getUserId(), status, started,
                "COMPLETED".equals(status) ? started.plus(10, ChronoUnit.MINUTES) : null);
        List<UUID> sqIds = new ArrayList<>();
        for (int i = 0; i < questionIds.size(); i++) {
            sqIds.add(ExamRows.attemptQuestion(jdbc, attemptId, questionIds.get(i), i + 1, threadStatuses.get(i)));
        }
        return new SessionFx(attemptId, student, sqIds);
    }

    public SessionFx completedSession(UUID examId, CourseFx course, User student, List<UUID> questionIds) {
        return session(examId, course, student, "COMPLETED", null, questionIds,
                questionIds.stream().map(q -> "DONE").toList());
    }

    private int turnOrder(UUID sessionId) {
        Integer n = jdbc.queryForObject("select coalesce(max(turn_order), 0) + 1 from exam_turns where attempt_id = ?",
                Integer.class, sessionId);
        return n == null ? 1 : n;
    }

    /** Adds a MAIN turn (parent null) or a FOLLOW_UP turn (parent set) to a thread; returns the turn id. */
    public UUID turn(SessionFx session, int threadIndex, UUID questionId, UUID parentTurnId, int followupIndex,
                     String status, String transcript, Integer durationSec, String answerAudioKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        insert into exam_turns (turn_id, attempt_id, attempt_question_id, question_id, parent_turn_id, turn_type,
                                                turn_order, followup_index, question_text, question_audio_key, language, status,
                                                asked_at, response_duration_sec, answer_audio_key, student_transcript,
                                                ai_analysis, followup_decision)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'VI', ?, ?, ?, ?, ?, cast(? as jsonb), ?)""",
                id, session.sessionId(), session.sessionQuestionIds().get(threadIndex), questionId, parentTurnId,
                parentTurnId == null ? "MAIN" : "FOLLOW_UP", turnOrder(session.sessionId()), followupIndex,
                "Question text " + followupIndex, "tts/" + id + ".mp3", status, Timestamp.from(Instant.now()), durationSec,
                answerAudioKey, transcript, "{\"coverage\":0.5}", "NEXT_COMPLETE");
        return id;
    }

    /** One answered MAIN turn with a transcript of the given words. */
    public UUID answered(SessionFx session, int threadIndex, UUID questionId, String transcript) {
        return turn(session, threadIndex, questionId, null, 0, "ANSWERED", transcript, 60, null);
    }

    public void releaseResults(UUID examId, Instant at) {
        jdbc.update("update viva_exams set results_released = true, results_released_at = ? where viva_exam_id = ?",
                Timestamp.from(at), examId);
    }

    public void event(UUID sessionId, String type) {
        jdbc.update("insert into attempt_events (event_id, attempt_id, type, payload) values (?, ?, ?, cast(? as jsonb))",
                UUID.randomUUID(), sessionId, type, "{\"note\":\"" + type + "\"}");
    }
}
