package swd392.group6.AIVES.exam;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.user.User;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.questionbank.BloomLevel.APPLY;
import static swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND;

/** Buổi thi lifecycle: publish, check-in window, stages, results (15 §2.1–§2.2, D47, D48, AC-C6, AC-C7). */
@IntegrationTest
@Import(ExamTestConfiguration.class)
class ExamLifecycleIntegrationTest extends ExamTestBase {

    @Autowired ExamStatusRefresher refresher;

    @BeforeEach
    void resetClock() {
        clock.set(T0);
    }

    /** Published exam with check-in [T0+1h, T0+2h], 2 questions per student, and the given students. */
    private UUID publishedExam(Course c, User... students) throws Exception {
        questions(c, c.topicA(), UNDERSTAND, 6);
        UUID exam = createExam(c, 2);
        addStudents(c, exam, students);
        publish(c, exam);
        return exam;
    }

    @Test
    void publishChecksStudentsAndThePoolPerRow_D48() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 2);
        questions(c, c.topicB(), APPLY, 1);
        UUID template = template(c, """
                [{"topicId":"%s","bloomLevel":"UNDERSTAND","count":2},{"topicId":"%s","bloomLevel":"APPLY","count":2}]"""
                .formatted(c.topicA(), c.topicB()), null);
        UUID exam = createExam(c, template, T0.plusSeconds(3600), T0.plusSeconds(7200), null);

        call(post("/api/v1/viva-exams/" + exam + "/publish"), c.token())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_STUDENTS"));
        addStudents(c, exam, data.student());
        call(get("/api/v1/viva-exams/" + exam + "/pool-check"), c.token())
                .andExpect(jsonPath("$.sufficient").value(false))
                .andExpect(jsonPath("$.shortages", hasSize(1)));
        call(post("/api/v1/viva-exams/" + exam + "/publish"), c.token())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POOL_TOO_SMALL"))
                .andExpect(jsonPath("$.rows[0].rowIndex").value(1))
                .andExpect(jsonPath("$.rows[0].required").value(2))
                .andExpect(jsonPath("$.rows[0].available").value(1));

        questions(c, c.topicB(), APPLY, 1);
        call(post("/api/v1/viva-exams/" + exam + "/publish"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.template.locked").value(true));
        assertThat(jdbc.queryForObject("select count(*) from exam_attempts where viva_exam_id = ?", Integer.class, exam))
                .as("no questions are drawn before check-in").isZero();
        call(post("/api/v1/viva-exams/" + exam + "/unpublish"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void stageFollowsTheCheckInWindow_AC_C6() throws Exception {
        Course c = course();
        User student = data.student();
        UUID exam = publishedExam(c, student);

        assertThat(myStage(student, exam)).isEqualTo("UPCOMING");
        clock.set(T0.plus(Duration.ofMinutes(61)));
        assertThat(myStage(student, exam)).isEqualTo("AVAILABLE");
        assertThat(examStatus(exam)).isEqualTo("OPEN");
        clock.set(T0.plus(Duration.ofMinutes(121)));
        assertThat(myStage(student, exam)).isEqualTo("MISSED");
        assertThat(examStatus(exam)).isEqualTo("CLOSED");
        call(get("/api/v1/me/viva-exams/" + exam), token(student))
                .andExpect(jsonPath("$.attemptId").doesNotExist())
                .andExpect(jsonPath("$.attemptsUsed").value(0))
                .andExpect(jsonPath("$.durationSec").value(240))
                .andExpect(jsonPath("$.mainQuestionCount").value(2));
    }

    @Test
    void draftExamsAreInvisibleToStudents() throws Exception {
        Course c = course();
        User student = data.student();
        UUID exam = createExam(c, 1);
        addStudents(c, exam, student);
        call(get("/api/v1/me/viva-exams"), token(student)).andExpect(jsonPath("$.total").value(0));
        call(get("/api/v1/me/viva-exams/" + exam), token(student)).andExpect(status().isNotFound());
    }

    @Test
    void schedulerOpensAndClosesOnTime() throws Exception {
        Course c = course();
        UUID exam = publishedExam(c, data.student());
        clock.set(T0.plus(Duration.ofMinutes(60)));
        refresher.refreshDue();
        assertThat(examStatus(exam)).isEqualTo("OPEN");
        clock.set(T0.plus(Duration.ofMinutes(121)));
        refresher.refreshDue();
        assertThat(examStatus(exam)).isEqualTo("CLOSED");
    }

    @Test
    void openNowThenCloseEndsCheckInButNotRunningAttempts_AC_C7() throws Exception {
        Course c = course();
        User early = data.student();
        User late = data.student();
        UUID exam = publishedExam(c, early, late);
        call(post("/api/v1/viva-exams/" + exam + "/open"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.checkinOpensAt").value(T0.toString()));
        UUID attempt = checkedIn(early, exam);
        call(post("/api/v1/viva-exams/" + exam + "/unpublish"), c.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_EXISTS"));
        call(post("/api/v1/viva-exams/" + exam + "/cancel"), c.token(), "{\"reason\":\"x\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_IN_PROGRESS"));

        clock.set(T0.plus(Duration.ofMinutes(5)));
        call(post("/api/v1/viva-exams/" + exam + "/close"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.checkinClosesAt").value(clock.instant().toString()));
        checkIn(late, exam).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECKIN_NOT_OPEN"));
        assertThat(myStage(late, exam)).isEqualTo("MISSED");
        assertThat(myStage(early, exam)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select status from exam_attempts where attempt_id = ?", String.class, attempt))
                .isEqualTo("IN_PROGRESS");
        call(get("/api/v1/viva-exams/" + exam), c.token())
                .andExpect(jsonPath("$.stageCounts.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.stageCounts.MISSED").value(1));
    }

    @Test
    void cancelNeedsAReason_AC_C7() throws Exception {
        Course c = course();
        User student = data.student();
        UUID exam = publishedExam(c, student);
        call(post("/api/v1/viva-exams/" + exam + "/cancel"), c.token(), "{}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        call(post("/api/v1/viva-exams/" + exam + "/cancel"), c.token(), "{\"reason\":\"Phòng máy hỏng\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Phòng máy hỏng"));
        assertThat(myStage(student, exam)).isEqualTo("CANCELLED");
        call(post("/api/v1/viva-exams/" + exam + "/cancel"), c.token(), "{\"reason\":\"x\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_CANCELLABLE"));
    }

    @Test
    void everyStageAndResultStatusIsDerived() throws Exception {
        Course c = course();
        User inProgress = data.student();
        User interrupted = data.student();
        User completed = data.student();
        User absent = data.student();
        UUID exam = publishedExam(c, inProgress, interrupted, completed, absent);
        clock.set(T0.plus(Duration.ofMinutes(70)));
        checkedIn(inProgress, exam);
        data.attemptState(checkedIn(interrupted, exam), "INTERRUPTED", null);
        UUID done = checkedIn(completed, exam);
        data.attemptState(done, "COMPLETED", clock.instant().plusSeconds(200));

        assertThat(myStage(inProgress, exam)).isEqualTo("IN_PROGRESS");
        assertThat(myStage(interrupted, exam)).isEqualTo("INTERRUPTED");
        assertThat(myStage(absent, exam)).isEqualTo("AVAILABLE");
        call(get("/api/v1/me/viva-exams/" + exam), token(completed))
                .andExpect(jsonPath("$.stage").value("COMPLETED"))
                .andExpect(jsonPath("$.resultStatus").value("PENDING"))
                .andExpect(jsonPath("$.attemptsUsed").value(1))
                .andExpect(jsonPath("$.attemptId").value(done.toString()))
                .andExpect(jsonPath("$.startedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.deadlineAt").value(clock.instant().plusSeconds(240).toString()))
                .andExpect(jsonPath("$.evaluationId").doesNotExist());

        data.evaluation(done, "CONFIRMED");
        call(get("/api/v1/me/viva-exams/" + exam), token(completed)).andExpect(jsonPath("$.resultStatus").value("PENDING"));
        call(post("/api/v1/viva-exams/" + exam + "/release-results"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultsReleased").value(true));
        call(get("/api/v1/me/viva-exams/" + exam), token(completed))
                .andExpect(jsonPath("$.resultStatus").value("RELEASED"))
                .andExpect(jsonPath("$.evaluationId").exists());
        call(get("/api/v1/viva-exams/" + exam + "/attempts"), c.token())
                .andExpect(jsonPath("$[2].evaluationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$[*].stage", contains("IN_PROGRESS", "INTERRUPTED", "COMPLETED", "AVAILABLE")))
                .andExpect(jsonPath("$[3].attemptId").doesNotExist());
        call(get("/api/v1/me/viva-exams?stage=COMPLETED"), token(completed)).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void retakeUsesTheSameTemplateForTheChosenStudents_AC_C5() throws Exception {
        Course c = course();
        User s1 = data.student();
        User s2 = data.student();
        User stranger = data.student();
        UUID exam = publishedExam(c, s1, s2);
        String body = json(call(post("/api/v1/viva-exams/" + exam + "/retake"), c.token(),
                "{\"checkinOpensAt\":\"" + T0.plusSeconds(86400) + "\",\"checkinClosesAt\":\"" + T0.plusSeconds(90000)
                        + "\",\"studentCodes\":\"" + s1.getStudentCode() + " " + stranger.getStudentCode() + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exam.status").value("DRAFT"))
                .andExpect(jsonPath("$.exam.title").value("Viva – Thi lại"))
                .andExpect(jsonPath("$.exam.retakeOfVivaExamId").value(exam.toString()))
                .andExpect(jsonPath("$.students.added", hasSize(1)))
                .andExpect(jsonPath("$.students.notInOriginal", contains(stranger.getStudentCode()))));
        String templateOfOriginal = JsonPath.read(json(call(get("/api/v1/viva-exams/" + exam), c.token())), "$.template.id");
        assertThat((String) JsonPath.read(body, "$.exam.template.id")).isEqualTo(templateOfOriginal);
    }
}
