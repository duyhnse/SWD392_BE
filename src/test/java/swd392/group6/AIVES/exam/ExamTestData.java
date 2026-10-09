package swd392.group6.AIVES.exam;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.questionbank.BloomLevel;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Inserts course content straight into the database (the question bank module is built in parallel). */
@Component
class ExamTestData {

    private final JdbcTemplate jdbc;
    private final TestUsers users;

    ExamTestData(JdbcTemplate jdbc, TestUsers users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    User lecturer() {
        return users.create(Role.LECTURER);
    }

    User admin() {
        return users.create(Role.ADMIN);
    }

    /** A student with a fresh, unique student code (upper case). */
    User student() {
        User s = users.create(Role.STUDENT);
        String code = "SE" + (100000 + ThreadLocalRandom.current().nextInt(899999)) + s.getUsername().substring(1, 5).toUpperCase();
        jdbc.update("update users set student_code = ? where user_id = ?", code, s.getUserId());
        s.setStudentCode(code);
        return s;
    }

    UUID course(User... lecturers) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into courses (course_id, code, name) values (?, ?, ?)", id,
                "C" + id.toString().substring(0, 8).toUpperCase(), "Course " + id.toString().substring(0, 4));
        for (User l : lecturers) {
            jdbc.update("insert into course_lecturers (course_id, lecturer_id) values (?, ?)", id, l.getUserId());
        }
        return id;
    }

    UUID topic(UUID courseId, User creator) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into topics (topic_id, course_id, name, created_by) values (?, ?, ?, ?)",
                id, courseId, "Topic " + id.toString().substring(0, 6), creator.getUserId());
        return id;
    }

    UUID rubric(UUID courseId, User creator) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into rubrics (rubric_id, course_id, name, created_by) values (?, ?, ?, ?)",
                id, courseId, "Rubric", creator.getUserId());
        jdbc.update("""
                insert into rubric_criteria (criterion_id, rubric_id, name, description, max_score, weight_percent)
                values (?, ?, 'Correctness', 'Correct answer', 10, 100)""", UUID.randomUUID(), id);
        return id;
    }

    UUID question(UUID courseId, UUID topicId, BloomLevel bloom, UUID rubricId, User owner) {
        return question(courseId, topicId, bloom, rubricId, owner, "PUBLISHED");
    }

    UUID question(UUID courseId, UUID topicId, BloomLevel bloom, UUID rubricId, User owner, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into questions (question_id, course_id, topic_id, content, reference_answer, bloom_level, language,
                                       status, origin, rubric_id, owner_id)
                values (?, ?, ?, ?, 'Reference', ?, 'VI', ?, 'MANUAL', ?, ?)""",
                id, courseId, topicId, "Question " + bloom + " " + id.toString().substring(0, 6), bloom.name(), status,
                rubricId, owner.getUserId());
        return id;
    }

    void sessionState(UUID sessionId, String status, Instant startedAt, Instant endedAt) {
        jdbc.update("update exam_sessions set status = ?, started_at = ?, ended_at = ?, deadline_at = ? where session_id = ?",
                status, ts(startedAt), ts(endedAt), startedAt == null ? null : ts(startedAt.plusSeconds(900)), sessionId);
    }

    void evaluation(UUID sessionId, String status) {
        jdbc.update("insert into grade_evaluations (evaluation_id, session_id, status) values (?, ?, ?)",
                UUID.randomUUID(), sessionId, status);
    }

    UUID sessionOf(UUID examId, User student) {
        return jdbc.queryForObject("select session_id from exam_sessions where viva_exam_id = ? and student_id = ?",
                UUID.class, examId, student.getUserId());
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
