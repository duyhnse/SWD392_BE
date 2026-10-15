package swd392.group6.AIVES.questionbank;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import swd392.group6.AIVES.support.ExamRows;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.support.TestUsers.PASSWORD;

/** Test data for the question bank: courses and assignments via SQL, content via the API. */
class QuestionBankFixture {

    record Actor(User user, String token) {
        UUID id() {
            return user.getUserId();
        }
    }

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final TestUsers users;

    QuestionBankFixture(MockMvc mvc, JdbcTemplate jdbc, TestUsers users) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.users = users;
    }

    Actor actor(Role role) throws Exception {
        User user = users.create(role);
        return new Actor(user, TestUsers.login(mvc, user.getUsername(), PASSWORD));
    }

    Actor lecturer() throws Exception {
        return actor(Role.LECTURER);
    }

    /** A course with one assigned lecturer. */
    UUID course(String defaultLanguage) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into courses (course_id, code, name, default_language) values (?, ?, ?, ?)",
                id, "C" + id.toString().substring(0, 8).toUpperCase(), "Course " + id, defaultLanguage);
        return id;
    }

    void assign(UUID courseId, Actor lecturer) {
        jdbc.update("insert into course_lecturers (course_id, lecturer_id) values (?, ?)", courseId, lecturer.id());
    }

    ResultActions perform(Actor actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + actor.token()));
    }

    ResultActions json(Actor actor, AbstractMockHttpServletRequestBuilder<?> request, String body) throws Exception {
        return perform(actor, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    static UUID id(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(body(result), "$.id"));
    }

    static <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    UUID topic(Actor lecturer, UUID courseId, String name) throws Exception {
        return id(json(lecturer, post("/api/v1/courses/" + courseId + "/topics"), "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated()));
    }

    /** Rubric with one criterion per weight, max score 10 each. */
    UUID rubric(Actor lecturer, UUID courseId, String name, int... weights) throws Exception {
        return id(json(lecturer, post("/api/v1/courses/" + courseId + "/rubrics"), rubricBody(name, weights))
                .andExpect(status().isCreated()));
    }

    static String rubricBody(String name, int... weights) {
        StringBuilder criteria = new StringBuilder();
        for (int i = 0; i < weights.length; i++) {
            if (i > 0) {
                criteria.append(',');
            }
            criteria.append("{\"name\":\"Criterion ").append(i + 1).append("\",\"description\":\"d\",\"maxScore\":10,")
                    .append("\"weightPercent\":").append(weights[i]).append('}');
        }
        return "{\"name\":\"" + name + "\",\"description\":\"r\",\"criteria\":[" + criteria + "]}";
    }

    UUID question(Actor lecturer, UUID courseId, String body) throws Exception {
        return id(json(lecturer, post("/api/v1/courses/" + courseId + "/questions"), body)
                .andExpect(status().isCreated()));
    }

    static String completeQuestion(UUID topicId, UUID rubricId, String content) {
        return "{\"topicId\":\"" + topicId + "\",\"content\":\"" + content + "\",\"referenceAnswer\":\"- point A\","
                + "\"bloomLevel\":\"UNDERSTAND\",\"rubricId\":\"" + rubricId + "\"}";
    }

    /** A fresh course + topic + valid rubric with the lecturer assigned. */
    record Bank(UUID courseId, UUID topicId, UUID rubricId) {
    }

    Bank bank(Actor lecturer) throws Exception {
        UUID courseId = course("VI");
        assign(courseId, lecturer);
        UUID topicId = topic(lecturer, courseId, "Architecture");
        UUID rubricId = rubric(lecturer, courseId, "Default rubric", 60, 40);
        return new Bank(courseId, topicId, rubricId);
    }

    UUID publishedQuestion(Actor lecturer, Bank bank, String content) throws Exception {
        UUID id = question(lecturer, bank.courseId(), completeQuestion(bank.topicId(), bank.rubricId(), content));
        json(lecturer, post("/api/v1/questions/publish"), "{\"questionIds\":[\"" + id + "\"]}")
                .andExpect(status().isOk());
        return id;
    }

    /** BR-Q8: what the interview module does when a session using the question completes. */
    void lock(UUID questionId) {
        jdbc.update("update questions set is_locked = true where question_id = ?", questionId);
        jdbc.update("update rubrics set is_locked = true where rubric_id = (select rubric_id from questions where question_id = ?)",
                questionId);
    }

    /** Puts the question into an attempt with the given status (drawn at check-in, D48). */
    void assignToSession(UUID courseId, UUID questionId, Actor examiner, String attemptStatus) {
        Instant now = Instant.now();
        UUID examId = ExamRows.exam(jdbc, courseId, examiner.id(), 1, now.minus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS), "OPEN");
        User student = users.create(Role.STUDENT);
        UUID attempt = ExamRows.attempt(jdbc, examId, student.getUserId(), attemptStatus, now.minus(10, ChronoUnit.MINUTES),
                "COMPLETED".equals(attemptStatus) ? now : null);
        ExamRows.attemptQuestion(jdbc, attempt, questionId, 1, "PENDING");
    }

    /** Puts the question into the SELECTED pool of a published buổi thi's template. */
    void selectInPublishedExam(UUID courseId, UUID questionId, Actor examiner) {
        Instant now = Instant.now();
        UUID examId = ExamRows.exam(jdbc, courseId, examiner.id(), 1, now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS), "READY");
        jdbc.update("""
                update exam_templates set question_pool_mode = 'SELECTED'
                where exam_template_id = (select exam_template_id from viva_exams where viva_exam_id = ?)""", examId);
        jdbc.update("""
                insert into exam_template_questions (exam_template_id, question_id)
                select exam_template_id, ? from viva_exams where viva_exam_id = ?""", questionId, examId);
    }

    static String ids(List<UUID> ids) {
        return "{\"questionIds\":[" + String.join(",", ids.stream().map(i -> "\"" + i + "\"").toList()) + "]}";
    }
}
