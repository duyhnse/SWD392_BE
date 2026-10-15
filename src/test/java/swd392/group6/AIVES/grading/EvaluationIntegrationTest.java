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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG4 lecturer grading (06, 15 §5.5): creation, manual review, confirmation, authorization. */
@IntegrationTest
class EvaluationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private JdbcTemplate jdbc;

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
        lecturerToken = token(course.lecturer());
    }

    private String token(User user) throws Exception {
        return "Bearer " + TestUsers.login(mockMvc, user.getUsername(), TestUsers.PASSWORD);
    }

    /** Completed lượt thi with two answered threads. */
    private SessionFx twoThreadSession(User student) {
        UUID q1 = fx.question(course, rubric.rubricId(), "Q1 " + ExamFixtures.unique());
        UUID q2 = fx.question(course, rubric.rubricId(), "Q2 " + ExamFixtures.unique());
        SessionFx session = fx.completedSession(examId, course, student, List.of(q1, q2));
        fx.answered(session, 0, q1, "microservice scale doc lap");
        fx.answered(session, 1, q2, "monolith de trien khai");
        return session;
    }

    private String createEvaluation(UUID sessionId) throws Exception {
        String json = mockMvc.perform(post("/api/v1/attempts/{id}/evaluation", sessionId).header("Authorization", lecturerToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String evaluationJson(String evaluationId) throws Exception {
        return mockMvc.perform(get("/api/v1/evaluations/{id}", evaluationId).header("Authorization", lecturerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions putThread(String evaluationId, String gradeId, int version, String criteriaJson,
                                    String extra) throws Exception {
        return mockMvc.perform(put("/api/v1/evaluations/{id}/threads/{g}", evaluationId, gradeId)
                .header("Authorization", lecturerToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + ",\"criteria\":" + criteriaJson + extra + "}"));
    }

    private String criteria(double first, double second) {
        return "[{\"criterionId\":\"" + rubric.criterionIds().get(0) + "\",\"finalScore\":" + first + "},"
                + "{\"criterionId\":\"" + rubric.criterionIds().get(1) + "\",\"finalScore\":" + second + "}]";
    }

    @Test
    void creationClassifiesThreadsAndIsIdempotent() throws Exception {
        User student = fx.student("Nguyễn Văn A");
        List<UUID> q = List.of(
                fx.question(course, rubric.rubricId(), "answered"), fx.question(course, rubric.rubricId(), "skipped"),
                fx.question(course, rubric.rubricId(), "not reached"), fx.question(course, rubric.rubricId(), "stt failed"),
                fx.question(course, rubric.rubricId(), "silent"));
        SessionFx s = fx.session(examId, course, student, "COMPLETED", null, q,
                List.of("DONE", "SKIPPED", "NOT_REACHED", "DONE", "DONE"));
        UUID main = fx.turn(s, 0, q.get(0), null, 0, "ANSWERED", "one two three four", 30, null);
        fx.turn(s, 0, q.get(0), main, 1, "ANSWERED", "five six", 30, null);
        fx.turn(s, 1, q.get(1), null, 0, "SKIPPED_BY_LECTURER", null, null, null);
        fx.turn(s, 3, q.get(3), null, 0, "TRANSCRIPTION_FAILED", null, 20, null);
        fx.turn(s, 4, q.get(4), null, 0, "NO_ANSWER", null, null, null);

        String json = mockMvc.perform(post("/api/v1/attempts/{id}/evaluation", s.sessionId()).header("Authorization", lecturerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_REVIEW"))
                .andExpect(jsonPath("$.student.studentCode").value(student.getStudentCode()))
                .andExpect(jsonPath("$.threads", hasSize(5)))
                .andExpect(jsonPath("$.threads[0].status").value("AI_FAILED"))
                .andExpect(jsonPath("$.threads[0].aiError").value("AI_NOT_REQUESTED"))
                .andExpect(jsonPath("$.threads[0].turns", hasSize(2)))
                .andExpect(jsonPath("$.threads[0].signals.words").value(6))
                .andExpect(jsonPath("$.threads[0].signals.totalAnswerSec").value(60))
                .andExpect(jsonPath("$.threads[0].signals.wordsPerMin").value(6))
                .andExpect(jsonPath("$.threads[0].signals.followupsUsed").value(1))
                .andExpect(jsonPath("$.threads[0].criteria", hasSize(2)))
                .andExpect(jsonPath("$.threads[0].criteria[0].weightPercent").value(60.0))
                .andExpect(jsonPath("$.threads[0].criteria[0].finalScore").doesNotExist())
                .andExpect(jsonPath("$.threads[0].question.referenceAnswer").value("SECRET reference answer"))
                .andExpect(jsonPath("$.threads[1].status").value("NOT_ASKED"))
                .andExpect(jsonPath("$.threads[1].includeInTotal").value(false))
                .andExpect(jsonPath("$.threads[2].status").value("NOT_ASKED"))
                .andExpect(jsonPath("$.threads[2].includeInTotal").value(true))
                .andExpect(jsonPath("$.threads[2].finalScore").value(0.0))
                .andExpect(jsonPath("$.threads[2].criteria[0].finalScore").value(0.0))
                .andExpect(jsonPath("$.threads[3].status").value("MISSING_DATA"))
                .andExpect(jsonPath("$.threads[4].status").value("AI_SCORED"))
                .andExpect(jsonPath("$.threads[4].aiScore").value(0.0))
                .andExpect(jsonPath("$.threads[4].aiFeedback").value("No answer"))
                .andExpect(jsonPath("$.aiTotalScore").value(0.0))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");

        mockMvc.perform(post("/api/v1/attempts/{id}/evaluation", s.sessionId()).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        assertThat(jdbc.queryForObject("select count(*) from grade_evaluations where attempt_id = ?", Integer.class,
                s.sessionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from criterion_scores cs join question_grades qg on qg.question_grade_id = cs.question_grade_id
                where qg.evaluation_id = ?::uuid""", Integer.class, id)).isEqualTo(10);
    }

    @Test
    void creationRequiresCompletedSessionAndRubric() throws Exception {
        UUID q = fx.question(course, rubric.rubricId(), "q");
        SessionFx running = fx.session(examId, course, fx.student("B"), "IN_PROGRESS", null, List.of(q), List.of("IN_PROGRESS"));
        mockMvc.perform(post("/api/v1/attempts/{id}/evaluation", running.sessionId()).header("Authorization", lecturerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_NOT_COMPLETED"));

        UUID noRubric = fx.question(course, null, "no rubric");
        SessionFx s = fx.completedSession(examId, course, fx.student("C"), List.of(noRubric));
        mockMvc.perform(post("/api/v1/attempts/{id}/evaluation", s.sessionId()).header("Authorization", lecturerToken))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RUBRIC_MISSING"));

        mockMvc.perform(post("/api/v1/attempts/{id}/evaluation", UUID.randomUUID()).header("Authorization", lecturerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTEMPT_NOT_FOUND"));
    }

    @Test
    void manualGradingAndConfirmComputeTheFormula_AC_C8() throws Exception {
        SessionFx s = twoThreadSession(fx.student("D"));
        String id = createEvaluation(s.sessionId());
        String json = evaluationJson(id);
        String g1 = JsonPath.read(json, "$.threads[0].gradeId");
        String g2 = JsonPath.read(json, "$.threads[1].gradeId");
        int version = JsonPath.read(json, "$.version");

        // incomplete: confirm lists both threads
        mockMvc.perform(post("/api/v1/evaluations/{id}/confirm", id).header("Authorization", lecturerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADES_INCOMPLETE"))
                .andExpect(jsonPath("$.threads", hasSize(2)))
                .andExpect(jsonPath("$.threads[0].orderNo").value(1));

        // 60/40 weights, finals 8 and 5 → 0.8×0.6×10 + 0.5×0.4×10 = 6.80
        json = putThread(id, g1, version, criteria(8, 5), ",\"lecturerComment\":\"Khá tốt\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threads[0].finalScore").value(6.8))
                .andExpect(jsonPath("$.threads[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.threads[0].lecturerComment").value("Khá tốt"))
                .andExpect(jsonPath("$.currentTotalScore").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        int v2 = JsonPath.read(json, "$.version");
        assertThat(v2).isGreaterThan(version);

        // stale version → 409
        putThread(id, g2, version, criteria(10, 10), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // only one thread left incomplete
        mockMvc.perform(post("/api/v1/evaluations/{id}/confirm", id).header("Authorization", lecturerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.threads", hasSize(1)))
                .andExpect(jsonPath("$.threads[0].gradeId").value(g2));

        json = putThread(id, g2, v2, criteria(10, 7.5), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threads[1].finalScore").value(9.0))
                .andExpect(jsonPath("$.currentTotalScore").value(7.9))
                .andReturn().getResponse().getContentAsString();
        int v3 = JsonPath.read(json, "$.version");

        mockMvc.perform(put("/api/v1/evaluations/{id}", id).header("Authorization", lecturerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + v3 + ",\"lecturerComment\":\"Tốt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecturerComment").value("Tốt"));

        mockMvc.perform(post("/api/v1/evaluations/{id}/confirm", id).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.finalTotalScore").value(7.9))
                .andExpect(jsonPath("$.confirmedBy").value(course.lecturer().getUserId().toString()));

        // BR-G6: read-only after confirmation
        int v4 = JsonPath.read(evaluationJson(id), "$.version");
        putThread(id, g1, v4, criteria(10, 10), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_CONFIRMED"));
        mockMvc.perform(post("/api/v1/evaluations/{id}/confirm", id).header("Authorization", lecturerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_CONFIRMED"));

        // BR-G7: change log
        mockMvc.perform(get("/api/v1/evaluations/{id}/history", id).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.field == 'final_score')]", hasSize(4)))
                .andExpect(jsonPath("$[?(@.field == 'lecturer_comment')].newValue").value("Khá tốt"))
                .andExpect(jsonPath("$[?(@.field == 'evaluation_comment')].newValue").value("Tốt"))
                .andExpect(jsonPath("$[?(@.field == 'status')].newValue").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].changedByName").value("Test LECTURER"))
                .andExpect(jsonPath("$[0].threadOrderNo").value(1));
    }

    @Test
    void finalScoresAreValidatedAndIncludeToggleIsLogged() throws Exception {
        SessionFx s = twoThreadSession(fx.student("E"));
        String id = createEvaluation(s.sessionId());
        String json = evaluationJson(id);
        String g1 = JsonPath.read(json, "$.threads[0].gradeId");
        String g2 = JsonPath.read(json, "$.threads[1].gradeId");
        int version = JsonPath.read(json, "$.version");

        putThread(id, g1, version, criteria(10.25, 5), "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SCORE_OUT_OF_RANGE"));
        putThread(id, g1, version, criteria(7.3, 5), "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SCORE_OUT_OF_RANGE"));
        putThread(id, g1, version, criteria(-1, 5), "")
                .andExpect(status().isUnprocessableContent());
        putThread(id, g1, version, "[{\"criterionId\":\"" + UUID.randomUUID() + "\",\"finalScore\":1}]", "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNKNOWN_CRITERION"));
        mockMvc.perform(put("/api/v1/evaluations/{id}/threads/{g}", id, g1).header("Authorization", lecturerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"criteria\":[]}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from grade_change_log where evaluation_id = ?::uuid",
                Integer.class, id)).isZero();

        json = putThread(id, g1, version, criteria(7.25, 9.75), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threads[0].criteria[0].finalScore").value(7.25))
                .andReturn().getResponse().getContentAsString();
        version = JsonPath.read(json, "$.version");
        // exclude thread 2 → it no longer blocks confirmation
        putThread(id, g2, version, "[]", ",\"includeInTotal\":false")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threads[1].includeInTotal").value(false));
        mockMvc.perform(post("/api/v1/evaluations/{id}/confirm", id).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                // 7.25/10×60% ×10 + 9.75/10×40% ×10 = 4.35 + 3.90 = 8.25
                .andExpect(jsonPath("$.finalTotalScore").value(8.25));
        assertThat(jdbc.queryForObject("""
                select count(*) from grade_change_log where evaluation_id = ?::uuid and field = 'include_in_total'
                and old_value = 'true' and new_value = 'false'""", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void acceptAiCopiesSuggestionsAndKeepsAiScores() throws Exception {
        SessionFx s = twoThreadSession(fx.student("F"));
        String id = createEvaluation(s.sessionId());
        // simulate the AI pipeline on thread 1 only
        jdbc.update("""
                update criterion_scores cs set ai_score = case when cs.criterion_id = ? then 8 else 5 end, ai_justification = 'ok'
                from question_grades qg where qg.question_grade_id = cs.question_grade_id and qg.evaluation_id = ?::uuid
                and qg.attempt_question_id = ?""", rubric.criterionIds().get(0), id, s.sessionQuestionIds().get(0));
        jdbc.update("""
                update question_grades set status = 'AI_SCORED', ai_score = 6.80, ai_error = null,
                       ai_strengths = '["clear"]'::jsonb, ai_feedback = 'Good'
                where evaluation_id = ?::uuid and attempt_question_id = ?""", id, s.sessionQuestionIds().get(0));
        String json = evaluationJson(id);
        String g2 = JsonPath.read(json, "$.threads[1].gradeId");

        mockMvc.perform(post("/api/v1/evaluations/{id}/threads/{g}/accept-ai", id, g2).header("Authorization", lecturerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_SCORE_NOT_AVAILABLE"));

        mockMvc.perform(post("/api/v1/evaluations/{id}/accept-all-ai", id).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threads[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.threads[0].finalScore").value(6.8))
                .andExpect(jsonPath("$.threads[0].aiScore").value(6.8))
                .andExpect(jsonPath("$.threads[0].aiStrengths[0]").value("clear"))
                .andExpect(jsonPath("$.threads[0].criteria[0].aiScore").value(8.0))
                .andExpect(jsonPath("$.threads[1].finalScore").doesNotExist());
        assertThat(jdbc.queryForObject("""
                select count(*) from grade_change_log where evaluation_id = ?::uuid and field = 'final_score'""",
                Integer.class, id)).isEqualTo(2);
    }

    @Test
    void listForExamShowsEveryStudent() throws Exception {
        SessionFx graded = twoThreadSession(fx.student("G"));
        String id = createEvaluation(graded.sessionId());
        UUID q = fx.question(course, rubric.rubricId(), "q");
        fx.session(examId, course, fx.student("H"), "NO_SHOW", null, List.of(q), List.of("PENDING"));

        mockMvc.perform(get("/api/v1/viva-exams/{id}/evaluations", examId).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].evaluationId").value(id))
                .andExpect(jsonPath("$.items[0].evaluationStatus").value("AWAITING_REVIEW"))
                .andExpect(jsonPath("$.items[0].aiFailedThreads").value(2))
                .andExpect(jsonPath("$.items[0].student.fullName").value("G"))
                .andExpect(jsonPath("$.items[1].attemptStatus").doesNotExist())
                .andExpect(jsonPath("$.items[1].evaluationId").doesNotExist());
    }

    @Test
    void authorizationPerRole_AC_C1() throws Exception {
        User student = fx.student("I");
        SessionFx s = twoThreadSession(student);
        String id = createEvaluation(s.sessionId());
        String g1 = JsonPath.read(evaluationJson(id), "$.threads[0].gradeId");
        String studentToken = token(student);
        String outsider = token(users.create(Role.LECTURER));
        String admin = token(users.create(Role.ADMIN));

        // students: 403 on every lecturer endpoint (AC-G8)
        mockMvc.perform(get("/api/v1/evaluations/{id}", id).header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/attempts/{id}/evaluation", s.sessionId()).header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/viva-exams/{id}/report", examId).header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        // unassigned lecturer: 404 (course invisible)
        mockMvc.perform(get("/api/v1/evaluations/{id}", id).header("Authorization", outsider))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/viva-exams/{id}/evaluations", examId).header("Authorization", outsider))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/evaluations/{id}/threads/{g}", id, g1).header("Authorization", outsider)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"criteria\":" + criteria(1, 1) + "}"))
                .andExpect(status().isNotFound());
        // ADMIN reads, cannot write
        mockMvc.perform(get("/api/v1/evaluations/{id}", id).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/evaluations/{id}/history", id).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/evaluations/{id}/confirm", id).header("Authorization", admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_READ_ONLY"));
        mockMvc.perform(get("/api/v1/evaluations/{id}", UUID.randomUUID()).header("Authorization", lecturerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_FOUND"));
        // a second assigned lecturer can grade too
        User colleague = users.create(Role.LECTURER);
        fx.assign(course.courseId(), colleague);
        mockMvc.perform(get("/api/v1/evaluations/{id}", id).header("Authorization", token(colleague)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"password"))));
    }
}
