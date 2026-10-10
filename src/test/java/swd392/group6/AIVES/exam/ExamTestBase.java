package swd392.group6.AIVES.exam;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import swd392.group6.AIVES.questionbank.BloomLevel;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.User;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers shared by the exam integration tests (each test class carries the annotations itself). */
abstract class ExamTestBase {

    static final Instant T0 = ExamTestConfiguration.T0;

    @Autowired MockMvc mockMvc;
    @Autowired ExamTestData data;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    private final Map<UUID, String> tokens = new HashMap<>();

    /** A course with one assigned lecturer, two topics and a rubric. */
    record Course(UUID id, User lecturer, String token, UUID topicA, UUID topicB, UUID rubric) {
    }

    Course course() throws Exception {
        User lecturer = data.lecturer();
        UUID id = data.course(lecturer);
        return new Course(id, lecturer, token(lecturer), data.topic(id, lecturer), data.topic(id, lecturer),
                data.rubric(id, lecturer));
    }

    List<UUID> questions(Course c, UUID topic, BloomLevel bloom, int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> data.question(c.id(), topic, bloom, c.rubric(), c.lecturer()))
                .toList();
    }

    String token(User user) throws Exception {
        String t = tokens.get(user.getUserId());
        if (t == null) {
            t = TestUsers.login(mockMvc, user.getUsername(), TestUsers.PASSWORD);
            tokens.put(user.getUserId(), t);
        }
        return t;
    }

    <B extends AbstractMockHttpServletRequestBuilder<B>> ResultActions call(B request, String token, String json) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    <B extends AbstractMockHttpServletRequestBuilder<B>> ResultActions call(B request, String token) throws Exception {
        return call(request, token, null);
    }

    /** A đề thi with the given rows JSON ({@code [{"topicId":…,"bloomLevel":…,"count":…}]}) and extra fields. */
    UUID template(Course c, String itemsJson, String extra) throws Exception {
        String json = "{\"title\":\"Đề\",\"items\":" + itemsJson + (extra == null ? "" : "," + extra) + "}";
        String body = call(post("/api/v1/courses/" + c.id() + "/exam-templates"), c.token(), json)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /** One "any topic, any Bloom" row of {@code n} questions × 120 s. */
    UUID template(Course c, int n) throws Exception {
        return template(c, "[{\"count\":" + n + ",\"secondsPerQuestion\":120}]", null);
    }

    /** DRAFT buổi thi of the template, check-in window [opens, closes], extra JSON fields appended. */
    UUID createExam(Course c, UUID templateId, Instant opens, Instant closes, String extra) throws Exception {
        String json = "{\"title\":\"Viva\",\"templateId\":\"" + templateId + "\",\"checkinOpensAt\":\"" + opens
                + "\",\"checkinClosesAt\":\"" + closes + "\"" + (extra == null ? "" : "," + extra) + "}";
        String body = call(post("/api/v1/courses/" + c.id() + "/viva-exams"), c.token(), json)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /** DRAFT buổi thi with a fresh {@code n}-question template, check-in T0+1h … T0+2h. */
    UUID createExam(Course c, int n) throws Exception {
        return createExam(c, template(c, n), T0.plusSeconds(3600), T0.plusSeconds(7200), null);
    }

    void addStudents(Course c, UUID examId, User... students) throws Exception {
        StringBuilder codes = new StringBuilder();
        for (User s : students) {
            codes.append(s.getStudentCode()).append(' ');
        }
        call(put("/api/v1/viva-exams/" + examId + "/students"), c.token(),
                "{\"studentCodes\":\"" + codes.toString().trim() + "\"}").andExpect(status().isOk());
    }

    String publish(Course c, UUID examId) throws Exception {
        return call(post("/api/v1/viva-exams/" + examId + "/publish"), c.token())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    ResultActions checkIn(User student, UUID examId) throws Exception {
        return call(post("/api/v1/me/viva-exams/" + examId + "/check-in"), token(student), "{\"consentRecording\":true}");
    }

    /** Checks the student in (must succeed) and returns the attempt id. */
    UUID checkedIn(User student, UUID examId) throws Exception {
        String body = json(checkIn(student, examId).andExpect(status().isOk()));
        return UUID.fromString(JsonPath.read(body, "$.attemptId"));
    }

    String json(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    String examStatus(UUID examId) {
        return jdbc.queryForObject("select status from viva_exams where viva_exam_id = ?", String.class, examId);
    }

    List<UUID> attemptQuestions(UUID attemptId) {
        return jdbc.queryForList("""
                select question_id from attempt_questions where attempt_id = ? and status <> 'VOIDED' order by order_no""",
                UUID.class, attemptId);
    }

    String myStage(User student, UUID examId) throws Exception {
        return JsonPath.read(json(call(get("/api/v1/me/viva-exams/" + examId), token(student))
                .andExpect(status().isOk())), "$.stage");
    }
}
