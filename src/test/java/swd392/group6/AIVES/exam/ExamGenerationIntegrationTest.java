package swd392.group6.AIVES.exam;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import swd392.group6.AIVES.questionbank.BloomLevel;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.user.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.questionbank.BloomLevel.APPLY;
import static swd392.group6.AIVES.questionbank.BloomLevel.REMEMBER;
import static swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND;

/** generate-sessions / reset-sessions, retakes and student removal (15 §2.3, §3; 07 §2). */
@IntegrationTest
@Import(ExamTestConfiguration.class)
class ExamGenerationIntegrationTest extends ExamTestBase {

    @BeforeEach
    void resetClock() {
        clock.set(T0);
    }

    @Test
    void blueprintGivesEveryStudentTheSameMixWithoutConsecutiveOverlap_AC_C4() throws Exception {
        Course c = course();
        Map<UUID, String> kind = new HashMap<>();
        questions(c, c.topicA(), UNDERSTAND, 4).forEach(q -> kind.put(q, "A-UNDERSTAND"));
        questions(c, c.topicB(), APPLY, 2).forEach(q -> kind.put(q, "B-APPLY"));
        questions(c, c.topicA(), APPLY, 2).forEach(q -> kind.put(q, "A-APPLY"));
        questions(c, c.topicB(), REMEMBER, 2).forEach(q -> kind.put(q, "B-REMEMBER"));
        UUID exam = createExam(c, 3);
        call(put("/api/v1/viva-exams/" + exam + "/blueprint"), c.token(),
                "{\"items\":[{\"topicId\":\"" + c.topicA() + "\",\"bloomLevel\":\"UNDERSTAND\",\"count\":2},"
                        + "{\"topicId\":\"" + c.topicB() + "\",\"bloomLevel\":\"APPLY\",\"count\":1}]}")
                .andExpect(status().isOk());
        List<User> students = List.of(data.student(), data.student(), data.student(), data.student());
        addStudents(c, exam, students.toArray(User[]::new));

        String body = generate(c, exam);

        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("READY");
        assertThat((List<?>) JsonPath.read(body, "$.sessions")).hasSize(4);
        assertThat(examStatus(exam)).isEqualTo("READY");
        List<UUID> previous = List.of();
        for (User s : students) {
            List<UUID> mine = sessionQuestions(data.sessionOf(exam, s));
            assertThat(mine.stream().map(kind::get)).containsExactly("A-UNDERSTAND", "A-UNDERSTAND", "B-APPLY");
            if (!previous.isEmpty()) {
                assertThat(mine).doesNotContainAnyElementsOf(previous);
            }
            previous = mine;
        }
        assertThat(jdbc.queryForList("select distinct examiner_id from exam_sessions where viva_exam_id = ?", UUID.class, exam))
                .containsExactly(c.lecturer().getUserId());
        call(get("/api/v1/viva-exams/" + exam), c.token())
                .andExpect(jsonPath("$.stageCounts.UPCOMING").value(4));
    }

    @Test
    void sameExamGivesTheSameAssignmentAfterReset() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 9);
        UUID exam = createExam(c, 3);
        List<User> students = List.of(data.student(), data.student(), data.student());
        addStudents(c, exam, students.toArray(User[]::new));
        generate(c, exam);
        Map<UUID, List<UUID>> first = new HashMap<>();
        students.forEach(s -> first.put(s.getUserId(), sessionQuestions(data.sessionOf(exam, s))));

        call(post("/api/v1/viva-exams/" + exam + "/reset-sessions"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        assertThat(jdbc.queryForObject("select count(*) from exam_sessions where viva_exam_id = ?", Integer.class, exam)).isZero();
        generate(c, exam);

        students.forEach(s -> assertThat(sessionQuestions(data.sessionOf(exam, s))).isEqualTo(first.get(s.getUserId())));
    }

    @Test
    void poolTooSmallIsReportedPerRow() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 1);
        questions(c, c.topicB(), APPLY, 3);
        UUID exam = createExam(c, 3);
        call(put("/api/v1/viva-exams/" + exam + "/blueprint"), c.token(),
                "{\"items\":[{\"topicId\":\"" + c.topicB() + "\",\"count\":1},"
                        + "{\"topicId\":\"" + c.topicA() + "\",\"bloomLevel\":\"UNDERSTAND\",\"count\":2}]}");
        addStudents(c, exam, data.student());

        call(post("/api/v1/viva-exams/" + exam + "/generate-sessions"), c.token())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POOL_TOO_SMALL"))
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0].rowIndex").value(1))
                .andExpect(jsonPath("$.rows[0].required").value(2))
                .andExpect(jsonPath("$.rows[0].available").value(1));
        assertThat(examStatus(exam)).isEqualTo("DRAFT");
    }

    @Test
    void smallPoolGeneratesWithOverlapWarning_AC_E2() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 3);
        UUID exam = createExam(c, 3);
        addStudents(c, exam, data.student(), data.student());

        String body = generate(c, exam);

        List<String> codes = JsonPath.read(body, "$.warnings[*].code");
        assertThat(codes).contains("OVERLAP_UNAVOIDABLE", "POOL_SMALL");
    }

    @Test
    void generationNeedsStudents() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 3);
        UUID exam = createExam(c, 3);

        call(post("/api/v1/viva-exams/" + exam + "/generate-sessions"), c.token())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_STUDENTS"));
    }

    @Test
    void resetIsRefusedOnceSomeoneStarted() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 4);
        UUID exam = createExam(c, 2);
        User s = data.student();
        addStudents(c, exam, s);
        generate(c, exam);
        data.sessionState(data.sessionOf(exam, s), "IN_PROGRESS", T0, null);

        call(post("/api/v1/viva-exams/" + exam + "/reset-sessions"), c.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_STARTED"));
    }

    @Test
    void removingAStudentAfterGenerationCancelsTheirSession() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 6);
        UUID exam = createExam(c, 2);
        User a = data.student();
        User b = data.student();
        User started = data.student();
        addStudents(c, exam, a, b, started);
        generate(c, exam);
        UUID sessionA = data.sessionOf(exam, a);
        data.sessionState(data.sessionOf(exam, started), "IN_PROGRESS", T0, null);

        call(delete("/api/v1/viva-exams/" + exam + "/students/" + a.getUserId()), c.token())
                .andExpect(status().isNoContent());
        call(delete("/api/v1/viva-exams/" + exam + "/students/" + started.getUserId()), c.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_STARTED"));

        assertThat(jdbc.queryForMap("select status, cancel_reason from exam_sessions where session_id = ?", sessionA))
                .containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "REMOVED_BY_LECTURER");
        assertThat(myStage(a, sessionA)).isEqualTo("CANCELLED");
        call(get("/api/v1/viva-exams/" + exam + "/students"), c.token())
                .andExpect(jsonPath("$[*].seqNo", contains(1, 2)))
                .andExpect(jsonPath("$[0].studentId").value(b.getUserId().toString()))
                .andExpect(jsonPath("$[0].stage").value("UPCOMING"));
        call(get("/api/v1/viva-exams/" + exam + "/sessions"), c.token())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].stage", hasItem("CANCELLED")));
    }

    @Test
    void retakeKeepsTheBlueprintAndAvoidsPreviousQuestions_AC_C5() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 6);
        questions(c, c.topicB(), APPLY, 4);
        UUID original = createExam(c, 3);
        call(put("/api/v1/viva-exams/" + original + "/blueprint"), c.token(),
                "{\"items\":[{\"topicId\":\"" + c.topicA() + "\",\"bloomLevel\":\"UNDERSTAND\",\"count\":2},"
                        + "{\"topicId\":\"" + c.topicB() + "\",\"bloomLevel\":\"APPLY\",\"count\":1}]}");
        User retaker = data.student();
        User other = data.student();
        User outsider = data.student();
        addStudents(c, original, retaker, other);
        generate(c, original);
        Set<UUID> before = Set.copyOf(sessionQuestions(data.sessionOf(original, retaker)));

        String body = json(call(post("/api/v1/viva-exams/" + original + "/retake"), c.token(),
                "{\"windowStart\":\"" + T0.plusSeconds(86400) + "\",\"windowEnd\":\"" + T0.plusSeconds(90000)
                        + "\",\"studentCodes\":\"" + retaker.getStudentCode() + " " + outsider.getStudentCode() + " SE000000\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exam.status").value("DRAFT"))
                .andExpect(jsonPath("$.exam.retakeOfVivaExamId").value(original.toString()))
                .andExpect(jsonPath("$.exam.mainQuestionCount").value(3))
                .andExpect(jsonPath("$.exam.blueprint", hasSize(2)))
                .andExpect(jsonPath("$.students.added[*].studentId", contains(retaker.getUserId().toString())))
                .andExpect(jsonPath("$.students.notInOriginal", contains(outsider.getStudentCode())))
                .andExpect(jsonPath("$.students.unknown", contains("SE000000"))));
        UUID retake = UUID.fromString(JsonPath.read(body, "$.exam.id"));

        String generated = generate(c, retake);

        List<UUID> now = sessionQuestions(data.sessionOf(retake, retaker));
        assertThat(now).hasSize(3).doesNotContainAnyElementsOf(before);
        assertThat((List<String>) JsonPath.read(generated, "$.warnings[*].code")).doesNotContain("RETAKE_OVERLAP_UNAVOIDABLE");
        Map<UUID, BloomLevel> bloom = new HashMap<>();
        jdbc.query("select question_id, bloom_level from questions where course_id = ?",
                rs -> { bloom.put(rs.getObject(1, UUID.class), BloomLevel.valueOf(rs.getString(2))); }, c.id());
        assertThat(now.stream().map(bloom::get)).containsExactly(UNDERSTAND, UNDERSTAND, APPLY);
    }
}
