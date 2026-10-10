package swd392.group6.AIVES.grading;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import swd392.group6.AIVES.grading.ExamFixtures.CourseFx;
import swd392.group6.AIVES.grading.ExamFixtures.RubricFx;
import swd392.group6.AIVES.grading.ExamFixtures.SessionFx;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG6 student results and FG5 phúc khảo (15 §5.5, AC-C8, AC-C9). */
@IntegrationTest
class ResultsAndDisputesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private JdbcTemplate jdbc;

    private ExamFixtures fx;
    private CourseFx course;
    private RubricFx rubric;
    private UUID examId;
    private String lecturerToken;
    private GradingClient grading;

    @BeforeEach
    void setUp() throws Exception {
        fx = new ExamFixtures(jdbc, users);
        course = fx.course();
        rubric = fx.rubric(course, 10, 60, 10, 40);
        examId = fx.exam(course, 2);
        lecturerToken = token(course.lecturer());
        grading = new GradingClient(mockMvc, lecturerToken);
    }

    private String token(User user) throws Exception {
        return "Bearer " + TestUsers.login(mockMvc, user.getUsername(), TestUsers.PASSWORD);
    }

    private SessionFx session(User student) {
        UUID q1 = fx.question(course, rubric.rubricId(), "Câu hỏi một");
        UUID q2 = fx.question(course, rubric.rubricId(), "Câu hỏi hai");
        SessionFx s = fx.completedSession(examId, course, student, List.of(q1, q2));
        fx.answered(s, 0, q1, "tra loi mot");
        fx.answered(s, 1, q2, "tra loi hai");
        return s;
    }

    private ResultActions dispute(String token, String evaluationId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/me/results/{id}/disputes", evaluationId).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void studentSeesOwnResultOnlyAfterConfirmationAndRelease_AC_C8() throws Exception {
        User student = fx.student("Trần Thị B");
        String studentToken = token(student);
        SessionFx s = session(student);
        String id = grading.create(s.sessionId());
        grading.grade(id, 0, 8, 5);
        grading.grade(id, 1, 10, 10);

        // not confirmed, not released
        mockMvc.perform(get("/api/v1/me/results").header("Authorization", studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/me/results/{id}", id).header("Authorization", studentToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESULT_NOT_FOUND"));

        grading.confirm(id);
        // confirmed but not released
        mockMvc.perform(get("/api/v1/me/results/{id}", id).header("Authorization", studentToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/attempts/{id}/turns", s.sessionId()).header("Authorization", studentToken))
                .andExpect(status().isForbidden());

        fx.releaseResults(examId, Instant.now());
        mockMvc.perform(get("/api/v1/me/results").header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].evaluationId").value(id))
                .andExpect(jsonPath("$[0].finalTotalScore").value(8.4));
        mockMvc.perform(get("/api/v1/me/results/{id}", id).header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalTotalScore").value(8.4))
                .andExpect(jsonPath("$.threads[0].finalScore").value(6.8))
                .andExpect(jsonPath("$.threads[0].questionContent").value("Câu hỏi một"))
                .andExpect(jsonPath("$.threads[0].criteria[0].finalScore").value(8.0))
                .andExpect(jsonPath("$.threads[1].finalScore").value(10.0))
                .andExpect(jsonPath("$.canDispute").value(true))
                .andExpect(content().string(not(containsString("SECRET reference answer"))))
                .andExpect(content().string(not(containsString("aiScore"))));
        mockMvc.perform(get("/api/v1/attempts/{id}/turns", s.sessionId()).header("Authorization", studentToken))
                .andExpect(status().isOk());

        // other students and lecturers cannot use /me/results
        String other = token(fx.student("Other"));
        mockMvc.perform(get("/api/v1/me/results/{id}", id).header("Authorization", other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/results").header("Authorization", lecturerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void disputeResolveReopensGradingThenReconfirm_AC_C9() throws Exception {
        User student = fx.student("Lê Văn C");
        String studentToken = token(student);
        SessionFx s = session(student);
        String id = grading.gradeAndConfirm(s.sessionId(), new double[] {8, 5}, new double[] {10, 10});
        String gradeId = JsonPath.read(grading.evaluation(id), "$.threads[0].gradeId");

        // not released yet → no dispute possible
        dispute(studentToken, id, "{\"reason\":\"Điểm câu 1 thấp\"}").andExpect(status().isNotFound());
        fx.releaseResults(examId, Instant.now());

        dispute(studentToken, id, "{\"reason\":\" \"}").andExpect(status().isBadRequest());
        dispute(studentToken, id, "{\"reason\":\"x\",\"questionGradeIds\":[\"" + UUID.randomUUID() + "\"]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION_GRADE"));
        dispute(token(fx.student("Someone")), id, "{\"reason\":\"x\"}").andExpect(status().isNotFound());

        String json = dispute(studentToken, id, "{\"reason\":\"Điểm câu 1 thấp\",\"questionGradeIds\":[\"" + gradeId + "\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.questionGradeIds[0]").value(gradeId))
                .andExpect(jsonPath("$.student.fullName").value("Lê Văn C"))
                .andReturn().getResponse().getContentAsString();
        String disputeId = JsonPath.read(json, "$.id");
        dispute(studentToken, id, "{\"reason\":\"again\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_ALREADY_OPEN"));
        mockMvc.perform(get("/api/v1/me/results/{id}", id).header("Authorization", studentToken))
                .andExpect(jsonPath("$.canDispute").value(false));

        mockMvc.perform(get("/api/v1/me/disputes").header("Authorization", studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].vivaExamId").value(examId.toString()));
        mockMvc.perform(get("/api/v1/viva-exams/{id}/disputes", examId).header("Authorization", lecturerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/v1/disputes/{id}", disputeId).header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/disputes/{id}", disputeId).header("Authorization", token(users.create(Role.LECTURER))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/disputes/{id}/resolve", disputeId).header("Authorization", lecturerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/disputes/{id}/resolve", disputeId).header("Authorization", lecturerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"Chấm lại câu 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.evaluationStatus").value("DISPUTED"))
                .andExpect(jsonPath("$.resolvedBy").value(course.lecturer().getUserId().toString()));
        mockMvc.perform(post("/api/v1/disputes/{id}/reject", disputeId).header("Authorization", lecturerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_OPEN"));

        // DISPUTED: hidden from the student, editable by the lecturer, confirm again
        mockMvc.perform(get("/api/v1/me/results/{id}", id).header("Authorization", studentToken))
                .andExpect(status().isNotFound());
        grading.grade(id, 0, 10, 5);
        mockMvc.perform(post("/api/v1/evaluations/{id}/confirm", id).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                // thread 1: 6 + 2 = 8.00; thread 2: 10 → 9.00
                .andExpect(jsonPath("$.finalTotalScore").value(9.0));
        mockMvc.perform(get("/api/v1/me/results/{id}", id).header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalTotalScore").value(9.0));
        mockMvc.perform(get("/api/v1/evaluations/{id}/history", id).header("Authorization", lecturerToken))
                .andExpect(jsonPath("$[?(@.field == 'status' && @.newValue == 'DISPUTED')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.field == 'final_total_score')].newValue", hasSize(2)));
    }

    @Test
    void rejectKeepsEvaluationConfirmed() throws Exception {
        User student = fx.student("D");
        String studentToken = token(student);
        String id = grading.gradeAndConfirm(session(student).sessionId(), new double[] {5, 5}, new double[] {5, 5});
        fx.releaseResults(examId, Instant.now());
        String disputeId = JsonPath.read(dispute(studentToken, id, "{\"reason\":\"Xin phúc khảo\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        User admin = users.create(Role.ADMIN);
        mockMvc.perform(post("/api/v1/disputes/{id}/reject", disputeId).header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"Không\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/disputes/{id}/reject", disputeId).header("Authorization", lecturerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"Điểm đúng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.resolution").value("Điểm đúng"))
                .andExpect(jsonPath("$.evaluationStatus").value("CONFIRMED"));
        String version = String.valueOf((int) JsonPath.read(grading.evaluation(id), "$.version"));
        mockMvc.perform(put("/api/v1/evaluations/{id}", id).header("Authorization", lecturerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + ",\"lecturerComment\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_CONFIRMED"));
        // a new dispute may be filed once the previous one is closed (still inside the window)
        dispute(studentToken, id, "{\"reason\":\"Lần hai\"}").andExpect(status().isCreated());
    }

    @Test
    void disputeWindowClosesSevenDaysAfterRelease() throws Exception {
        User student = fx.student("E");
        String id = grading.gradeAndConfirm(session(student).sessionId(), new double[] {5, 5}, new double[] {5, 5});
        fx.releaseResults(examId, Instant.now().minus(8, ChronoUnit.DAYS));
        String studentToken = token(student);
        mockMvc.perform(get("/api/v1/me/results/{id}", id).header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDispute").value(false));
        dispute(studentToken, id, "{\"reason\":\"Muộn\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_WINDOW_CLOSED"));
    }
}
