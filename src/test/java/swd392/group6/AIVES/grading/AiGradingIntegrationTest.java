package swd392.group6.AIVES.grading;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.grading.ExamFixtures.CourseFx;
import swd392.group6.AIVES.grading.ExamFixtures.RubricFx;
import swd392.group6.AIVES.grading.ExamFixtures.SessionFx;
import swd392.group6.AIVES.interview.AttemptCompletedEvent;
import swd392.group6.AIVES.support.AiNodeTestDouble;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.User;

import java.util.List;
import org.springframework.http.MediaType;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** WF4 AI grading pipeline (06 §4–§6): completion → GRADE_THREAD jobs → AI fields → review; failures, retry, privacy. */
@IntegrationTest
class AiGradingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AiNodeTestDouble aiNode;
    @Autowired private ApplicationEventPublisher events;

    private ExamFixtures fx;
    private CourseFx course;
    private RubricFx rubric;
    private UUID examId;
    private String lecturerToken;

    @BeforeEach
    void setUp() throws Exception {
        fx = new ExamFixtures(jdbc, users);
        course = fx.course();
        rubric = fx.rubric(course, 10, 60, 10, 40);
        examId = fx.exam(course, 2);
        lecturerToken = "Bearer " + TestUsers.login(mockMvc, course.lecturer().getUsername(), TestUsers.PASSWORD);
    }

    private SessionFx session(User student, String transcript1, String transcript2) {
        UUID q1 = fx.question(course, rubric.rubricId(), "Q1 " + ExamFixtures.unique());
        UUID q2 = fx.question(course, rubric.rubricId(), "Q2 " + ExamFixtures.unique());
        SessionFx s = fx.completedSession(examId, course, student, List.of(q1, q2));
        fx.answered(s, 0, q1, transcript1);
        fx.answered(s, 1, q2, transcript2);
        return s;
    }

    private String evaluationOf(UUID attemptId) {
        return jdbc.queryForObject("select evaluation_id::text from grade_evaluations where attempt_id = ?", String.class, attemptId);
    }

    private String json(String evaluationId) throws Exception {
        return mockMvc.perform(get("/api/v1/evaluations/{id}", evaluationId).header("Authorization", lecturerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void aCompletedAttemptIsGradedByTheAiAndTheExaminerIsTold_AC_G1() throws Exception {
        SessionFx s = session(fx.student("Trần Thị B"), "microservice scale doc lap", "monolith de trien khai");
        events.publishEvent(new AttemptCompletedEvent(s.sessionId(), course.courseId(), List.of(), "ALL_DONE"));

        String id = evaluationOf(s.sessionId());
        String body = json(id);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("AWAITING_REVIEW");
        assertThat((List<String>) JsonPath.read(body, "$.threads[*].status")).containsOnly("AI_SCORED");
        assertThat((Double) JsonPath.read(body, "$.aiTotalScore")).isEqualTo(7.5);   // 0.75×0.6×10 + 0.75×0.4×10
        assertThat((Object) JsonPath.read(body, "$.finalTotalScore")).isNull();      // the AI never finalises (BR-G1)
        assertThat((String) JsonPath.read(body, "$.aiGeneralFeedback")).isEqualTo("double summary");
        assertThat(jdbc.queryForObject("select count(*) from notifications where type = 'GRADING_READY' and payload->>'evaluationId' = ?",
                Integer.class, id)).isEqualTo(1);

        events.publishEvent(new AttemptCompletedEvent(s.sessionId(), course.courseId(), List.of(), "ALL_DONE"));
        assertThat(jdbc.queryForObject("select count(*) from grade_evaluations where attempt_id = ?", Integer.class,
                s.sessionId())).isEqualTo(1);                                          // idempotent
    }

    @Test
    void theAiGetsTheThreadButNeitherTheStudentNorTheSignals_BR_I8_BR_G5() throws Exception {
        User student = fx.student("Lê Văn Bí Mật");
        aiNode.gradeInputs.clear();
        SessionFx s = session(student, "câu trả lời một", "câu trả lời hai");
        events.publishEvent(new AttemptCompletedEvent(s.sessionId(), course.courseId(), List.of(), "ALL_DONE"));

        assertThat(aiNode.gradeInputs).hasSize(2);
        String sent = aiNode.gradeInputs.getFirst().toString();
        assertThat(sent).contains("\"reference_answer\"", "\"criterion_id\"", "\"max_score\"", "\"transcript\":\"câu trả lời");
        assertThat(sent).doesNotContain("Bí Mật", student.getStudentCode(), student.getUsername(), "duration", "words_per_min");
    }

    @Test
    void anInvalidAiAnswerFailsOneThreadOnlyAndCanBeRetried_AC_G3() throws Exception {
        SessionFx s = session(fx.student("C"), "#ai-fail gibberish", "monolith de trien khai");
        events.publishEvent(new AttemptCompletedEvent(s.sessionId(), course.courseId(), List.of(), "ALL_DONE"));
        String id = evaluationOf(s.sessionId());
        String body = json(id);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("AWAITING_REVIEW");
        assertThat((String) JsonPath.read(body, "$.threads[0].status")).isEqualTo("AI_FAILED");
        assertThat((String) JsonPath.read(body, "$.threads[0].aiError")).startsWith("INVALID_RESULT");
        assertThat((String) JsonPath.read(body, "$.threads[1].status")).isEqualTo("AI_SCORED");
        assertThat((Double) JsonPath.read(body, "$.aiTotalScore")).isEqualTo(7.5);   // only the graded thread

        String gradeId = JsonPath.read(body, "$.threads[0].gradeId");
        jdbc.update("update exam_turns set student_transcript = 'microservice scale doc lap' where attempt_id = ?", s.sessionId());
        mockMvc.perform(post("/api/v1/evaluations/{id}/threads/{g}/retry-ai", id, gradeId).header("Authorization", lecturerToken))
                .andExpect(status().isOk());
        body = json(id);
        assertThat((String) JsonPath.read(body, "$.threads[0].status")).isEqualTo("AI_SCORED");
        assertThat((Object) JsonPath.read(body, "$.threads[0].aiError")).isNull();
    }

    private int version(String evaluationId) throws Exception {
        return JsonPath.read(json(evaluationId), "$.version");
    }

    @Test
    void oneQuestionTakesTheAiScoreAnotherIsGradedByHand_thenConfirm() throws Exception {
        SessionFx s = session(fx.student("Mixed"), "microservice scale doc lap", "monolith de trien khai");
        events.publishEvent(new AttemptCompletedEvent(s.sessionId(), course.courseId(), List.of(), "ALL_DONE"));
        String id = evaluationOf(s.sessionId());
        String body = json(id);
        String q1 = JsonPath.read(body, "$.threads[0].gradeId"), q2 = JsonPath.read(body, "$.threads[1].gradeId");
        List<String> c2 = JsonPath.read(body, "$.threads[1].criteria[*].criterionId");

        mockMvc.perform(post("/api/v1/evaluations/{id}/threads/{g}/accept-ai", id, q1).contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(id) + "}")
                .header("Authorization", lecturerToken)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/evaluations/{id}/threads/{g}", id, q2).header("Authorization", lecturerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(id) + ",\"criteria\":[{\"criterionId\":\"" + c2.get(0)
                        + "\",\"finalScore\":4},{\"criterionId\":\"" + c2.get(1) + "\",\"finalScore\":2}]}"))
                .andExpect(status().isOk());
        // "accept all AI" afterwards fills only what is still empty: the hand-graded question keeps the lecturer's scores
        mockMvc.perform(post("/api/v1/evaluations/{id}/accept-all-ai", id).contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(id) + "}")
                .header("Authorization", lecturerToken)).andExpect(status().isOk());
        body = json(id);
        assertThat((Double) JsonPath.read(body, "$.threads[0].finalScore")).isEqualTo(7.5);   // AI
        assertThat((Double) JsonPath.read(body, "$.threads[1].finalScore")).isEqualTo(3.2);   // 4×0.6 + 2×0.4
        mockMvc.perform(post("/api/v1/evaluations/{id}/confirm", id).contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(id) + "}")
                .header("Authorization", lecturerToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.finalTotalScore").value(5.35));
    }

    @Test
    void anEnglishQuestionInAVietnameseCourseIsGradedInEnglish_theOverallCommentInTheCourseLanguage_D77() throws Exception {
        SessionFx s = session(fx.student("Lang"), "dependency inversion means depending on abstractions", "monolith de trien khai");
        jdbc.update("update attempt_questions set language = 'EN' where attempt_id = ? and order_no = 1", s.sessionId());
        events.publishEvent(new AttemptCompletedEvent(s.sessionId(), course.courseId(), List.of(), "ALL_DONE"));
        String id = evaluationOf(s.sessionId());
        List<String> threadLanguages = jdbc.queryForList("""
                select j.request->>'language' from ai_jobs j join question_grades g on g.question_grade_id = j.subject_id
                join attempt_questions aq on aq.attempt_question_id = g.attempt_question_id
                where j.job_type = 'GRADE_THREAD' and g.evaluation_id = ?::uuid order by aq.order_no""", String.class, id);
        assertThat(threadLanguages).containsExactly("EN", "VI");                       // each question in its own language
        assertThat(jdbc.queryForObject("select request->>'language' from ai_jobs where job_type = 'SUMMARIZE_EVALUATION'"
                + " and subject_id = ?::uuid", String.class, id)).isEqualTo("VI");    // the course's lecturer reads it
        String body = json(id);
        assertThat((String) JsonPath.read(body, "$.threads[0].question.language")).isEqualTo("EN");
    }

    @Test
    void retryIsRefusedWithoutATranscriptAndAfterConfirmation() throws Exception {
        UUID q1 = fx.question(course, rubric.rubricId(), "Q " + ExamFixtures.unique());
        SessionFx s = fx.completedSession(examId, course, fx.student("D"), List.of(q1));
        fx.turn(s, 0, q1, null, 0, "TRANSCRIPTION_FAILED", null, 20, null);
        events.publishEvent(new AttemptCompletedEvent(s.sessionId(), course.courseId(), List.of(), "ALL_DONE"));
        String id = evaluationOf(s.sessionId());
        String gradeId = JsonPath.read(json(id), "$.threads[0].gradeId");
        mockMvc.perform(post("/api/v1/evaluations/{id}/threads/{g}/retry-ai", id, gradeId).header("Authorization", lecturerToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AI_RETRY_NOT_ALLOWED"));  // MISSING_DATA
    }
}
