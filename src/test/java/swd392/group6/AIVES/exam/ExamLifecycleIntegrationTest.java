package swd392.group6.AIVES.exam;

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
import static swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND;

/** Exam lifecycle, scheduler, stages and result visibility (15 §2.1–§2.2, BR-E4/E5, AC-C6, AC-C7). */
@IntegrationTest
@Import(ExamTestConfiguration.class)
class ExamLifecycleIntegrationTest extends ExamTestBase {

    @Autowired ExamStatusRefresher refresher;

    @BeforeEach
    void resetClock() {
        clock.set(T0);
    }

    /** READY exam with window [T0+1h, T0+2h] and the given students. */
    private UUID readyExam(Course c, User... students) throws Exception {
        questions(c, c.topicA(), UNDERSTAND, 2 * students.length + 2);
        UUID exam = createExam(c, 2);
        addStudents(c, exam, students);
        generate(c, exam);
        return exam;
    }

    @Test
    void stageFollowsTheWindow_AC_C6() throws Exception {
        Course c = course();
        User s = data.student();
        UUID exam = readyExam(c, s);
        UUID session = data.sessionOf(exam, s);

        call(get("/api/v1/me/sessions"), token(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].stage").value("UPCOMING"))
                .andExpect(jsonPath("$.items[0].resultStatus").value("NONE"))
                .andExpect(jsonPath("$.items[0].attemptsAllowed").value(1))
                .andExpect(jsonPath("$.items[0].attemptsUsed").value(0))
                .andExpect(jsonPath("$.items[0].mainQuestionCount").value(2))
                .andExpect(jsonPath("$.items[0].timeLimitPerStudentSec").value(900))
                .andExpect(jsonPath("$.items[0].courseCode").exists())
                .andExpect(jsonPath("$.items[0].questions").doesNotExist());

        clock.set(T0.plus(Duration.ofMinutes(90)));
        assertThat(myStage(s, session)).isEqualTo("AVAILABLE");
        assertThat(examStatus(exam)).isEqualTo("OPEN");

        clock.set(T0.plus(Duration.ofHours(3)));
        assertThat(myStage(s, session)).isEqualTo("MISSED");
        assertThat(examStatus(exam)).isEqualTo("CLOSED");
        call(get("/api/v1/me/sessions?stage=MISSED"), token(s)).andExpect(jsonPath("$.total").value(1));
        call(get("/api/v1/me/sessions?stage=UPCOMING"), token(s)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void schedulerOpensAndClosesOnTime() throws Exception {
        Course c = course();
        User s = data.student();
        UUID exam = readyExam(c, s);
        ExamScheduler scheduler = new ExamScheduler(refresher);

        clock.set(T0.plus(Duration.ofMinutes(61)));
        scheduler.tick();
        assertThat(examStatus(exam)).isEqualTo("OPEN");

        clock.set(T0.plus(Duration.ofMinutes(121)));
        scheduler.tick();
        assertThat(examStatus(exam)).isEqualTo("CLOSED");
        assertThat(jdbc.queryForMap("select status, cancel_reason from exam_sessions where viva_exam_id = ?", exam))
                .containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "NO_SHOW");
    }

    @Test
    void openNowThenCloseTurnsScheduledIntoNoShows_AC_C7() throws Exception {
        Course c = course();
        User absent = data.student();
        User taking = data.student();
        UUID exam = readyExam(c, absent, taking);

        call(post("/api/v1/viva-exams/" + exam + "/close"), c.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_OPEN"));
        call(post("/api/v1/viva-exams/" + exam + "/open"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.windowStart").value(T0.toString()));
        assertThat(myStage(absent, data.sessionOf(exam, absent))).isEqualTo("AVAILABLE");
        clock.advance(Duration.ofMinutes(5));
        data.sessionState(data.sessionOf(exam, taking), "IN_PROGRESS", T0.plusSeconds(60), null);

        call(post("/api/v1/viva-exams/" + exam + "/close"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.stageCounts.MISSED").value(1))
                .andExpect(jsonPath("$.stageCounts.IN_PROGRESS").value(1));
        assertThat(myStage(absent, data.sessionOf(exam, absent))).isEqualTo("MISSED");
        assertThat(myStage(taking, data.sessionOf(exam, taking))).isEqualTo("IN_PROGRESS");
        clock.advance(Duration.ofSeconds(1));
        call(get("/api/v1/courses/" + c.id() + "/viva-exams?when=past"), c.token())
                .andExpect(jsonPath("$.items[*].id", contains(exam.toString())));
    }

    @Test
    void cancelNeedsAReasonAndNobodyInProgress_AC_C7() throws Exception {
        Course c = course();
        User s = data.student();
        User other = data.student();
        UUID exam = readyExam(c, s, other);
        String url = "/api/v1/viva-exams/" + exam + "/cancel";

        call(post(url), c.token()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        call(post(url), c.token(), "{\"reason\":\"  \"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

        UUID session = data.sessionOf(exam, other);
        data.sessionState(session, "IN_PROGRESS", T0, null);
        call(post(url), c.token(), "{\"reason\":\"Phòng thi mất điện\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_IN_PROGRESS"));
        data.sessionState(session, "COMPLETED", T0, T0.plusSeconds(600));

        call(post(url), c.token(), "{\"reason\":\"Phòng thi mất điện\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Phòng thi mất điện"));
        assertThat(jdbc.queryForObject("select cancel_reason from exam_sessions where session_id = ?", String.class,
                data.sessionOf(exam, s))).isEqualTo("EXAM_CANCELLED");
        assertThat(myStage(s, data.sessionOf(exam, s))).isEqualTo("CANCELLED");
        assertThat(myStage(other, session)).isEqualTo("COMPLETED");
        call(post(url), c.token(), "{\"reason\":\"again\"}").andExpect(status().isConflict());
    }

    @Test
    void everyStageAndResultStatusIsDerived() throws Exception {
        Course c = course();
        User inProgress = data.student();
        User interrupted = data.student();
        User completed = data.student();
        UUID exam = readyExam(c, inProgress, interrupted, completed);
        clock.set(T0.plus(Duration.ofMinutes(70)));
        data.sessionState(data.sessionOf(exam, inProgress), "IN_PROGRESS", clock.instant(), null);
        data.sessionState(data.sessionOf(exam, interrupted), "INTERRUPTED", clock.instant(), null);
        UUID done = data.sessionOf(exam, completed);
        data.sessionState(done, "COMPLETED", clock.instant(), clock.instant().plusSeconds(500));

        assertThat(myStage(inProgress, data.sessionOf(exam, inProgress))).isEqualTo("IN_PROGRESS");
        assertThat(myStage(interrupted, data.sessionOf(exam, interrupted))).isEqualTo("INTERRUPTED");
        call(get("/api/v1/me/sessions/" + done), token(completed))
                .andExpect(jsonPath("$.stage").value("COMPLETED"))
                .andExpect(jsonPath("$.resultStatus").value("PENDING"))
                .andExpect(jsonPath("$.attemptsUsed").value(1))
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.endedAt").exists())
                .andExpect(jsonPath("$.evaluationId").doesNotExist());

        data.evaluation(done, "CONFIRMED");
        call(get("/api/v1/me/sessions/" + done), token(completed)).andExpect(jsonPath("$.resultStatus").value("PENDING"));
        call(post("/api/v1/viva-exams/" + exam + "/release-results"), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultsReleased").value(true))
                .andExpect(jsonPath("$.resultsReleasedAt").exists());
        call(get("/api/v1/me/sessions/" + done), token(completed))
                .andExpect(jsonPath("$.resultStatus").value("RELEASED"))
                .andExpect(jsonPath("$.evaluationId").exists());
        call(get("/api/v1/viva-exams/" + exam + "/sessions"), c.token())
                .andExpect(jsonPath("$[2].evaluationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$[*].stage", contains("IN_PROGRESS", "INTERRUPTED", "COMPLETED")));

        call(post("/api/v1/viva-exams/" + exam + "/unrelease-results"), c.token())
                .andExpect(jsonPath("$.resultsReleased").value(false));
        call(get("/api/v1/me/sessions/" + done), token(completed)).andExpect(jsonPath("$.resultStatus").value("PENDING"));

        // Somebody else's lượt thi does not exist for this student.
        call(get("/api/v1/me/sessions/" + done), token(inProgress)).andExpect(status().isNotFound());
        call(get("/api/v1/me/sessions?stage=COMPLETED"), token(completed)).andExpect(jsonPath("$.total").value(1));
    }
}
