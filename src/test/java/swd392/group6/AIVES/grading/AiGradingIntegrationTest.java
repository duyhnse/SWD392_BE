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
import swd392.group6.AIVES.support.FakeAiNode;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.User;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** WF4 AI grading pipeline (06 §4–§6): completion → GRADE_THREAD jobs → AI fields → review; failures, retry, privacy. */
@IntegrationTest
class AiGradingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FakeAiNode aiNode;
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
        assertThat((String) JsonPath.read(body, "$.aiGeneralFeedback")).isEqualTo("fake summary");
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
