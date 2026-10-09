package swd392.group6.AIVES.exam;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.user.User;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.questionbank.BloomLevel.APPLY;
import static swd392.group6.AIVES.questionbank.BloomLevel.REMEMBER;
import static swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND;

/** Check-in: questions drawn at the start of each attempt, snapshot and locking (D48, D49, AC-C4, AC-C5). */
@IntegrationTest
@Import(ExamTestConfiguration.class)
class CheckInIntegrationTest extends ExamTestBase {

    @BeforeEach
    void resetClock() {
        clock.set(T0);
    }

    /** Published, check-in open from T0 for 2 h. */
    private UUID openExam(Course c, UUID template, User... students) throws Exception {
        UUID exam = createExam(c, template, T0, T0.plusSeconds(7200), null);
        addStudents(c, exam, students);
        publish(c, exam);
        return exam;
    }

    @Test
    void everyStudentGetsTheTemplateMixWithoutOverlapWithThePreviousOne_AC_C4() throws Exception {
        Course c = course();
        List<UUID> understand = questions(c, c.chapterA(), UNDERSTAND, 4);
        List<UUID> apply = questions(c, c.chapterB(), APPLY, 2);
        questions(c, c.chapterA(), APPLY, 1);        // distractors: right chapter or right level only
        questions(c, c.chapterB(), UNDERSTAND, 1);
        UUID template = template(c, """
                [{"chapterId":"%s","bloomLevel":"UNDERSTAND","count":2,"secondsPerQuestion":150},
                 {"chapterId":"%s","bloomLevel":"APPLY","count":1,"secondsPerQuestion":300}]"""
                .formatted(c.chapterA(), c.chapterB()), null);
        List<User> students = List.of(data.student(), data.student(), data.student(), data.student());
        UUID exam = openExam(c, template, students.toArray(User[]::new));

        List<List<UUID>> drawn = new ArrayList<>();
        for (User s : students) {
            UUID attempt = checkedIn(s, exam);
            List<UUID> mine = attemptQuestions(attempt);
            assertThat(mine).hasSize(3);
            // Bloom ascending: the two UNDERSTAND questions first
            assertThat(understand).contains(mine.get(0), mine.get(1));
            assertThat(apply).contains(mine.get(2));
            if (!drawn.isEmpty()) {
                assertThat(mine).doesNotContainAnyElementsOf(drawn.getLast());
            }
            drawn.add(mine);
            Map<String, Object> row = jdbc.queryForMap(
                    "select extract(epoch from deadline_at - started_at)::int as length, selection_seed, consent_recorded_at from exam_attempts where attempt_id = ?",
                    attempt);
            assertThat(row.get("length")).isEqualTo(600);   // 2 × 150 s + 300 s (D46)
            assertThat(row.get("selection_seed")).isNotNull();
            assertThat(row.get("consent_recorded_at")).isNotNull();
            assertThat(jdbc.queryForObject("select type from attempt_events where attempt_id = ?", String.class, attempt))
                    .isEqualTo("CHECKED_IN");
        }
        call(get("/api/v1/viva-exams/" + exam), c.token()).andExpect(jsonPath("$.stageCounts.IN_PROGRESS").value(4));
    }

    @Test
    void theDrawnQuestionIsSnapshotAndLocked_D49() throws Exception {
        Course c = course();
        UUID q = questions(c, c.chapterA(), REMEMBER, 1).getFirst();
        UUID templateRubric = UUID.randomUUID();
        jdbc.update("insert into rubrics (rubric_id, course_id, name, created_by) values (?, ?, 'Template rubric', ?)",
                templateRubric, c.id(), c.lecturer().getUserId());
        jdbc.update("""
                insert into rubric_criteria (criterion_id, rubric_id, name, description, max_score, weight_percent)
                values (?, ?, 'Depth', 'Explains why', 10, 100)""", UUID.randomUUID(), templateRubric);
        UUID template = template(c, "[{\"bloomLevel\":\"REMEMBER\",\"count\":1}]",
                "\"rubricId\":\"" + templateRubric + "\"");
        User student = data.student();
        UUID exam = openExam(c, template, student);

        String mine = json(checkIn(student, exam).andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.attemptsUsed").value(1)));
        assertThat(mine).doesNotContain("Question ");          // students never see question content before the runner
        UUID attempt = UUID.fromString(JsonPath.read(mine, "$.attemptId"));

        assertThat(jdbc.queryForObject("select is_locked from questions where question_id = ?", Boolean.class, q)).isTrue();
        assertThat(jdbc.queryForObject("select is_locked from rubrics where rubric_id = ?", Boolean.class, templateRubric)).isTrue();
        String original = jdbc.queryForObject("select content from questions where question_id = ?", String.class, q);
        // Even a direct change of the bank row does not reach the attempt.
        jdbc.update("update questions set content = 'Changed later' where question_id = ?", q);

        call(get("/api/v1/attempts/" + attempt), c.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(jsonPath("$.questions[0].content").value(original))
                .andExpect(jsonPath("$.questions[0].referenceAnswer").value("Reference"))
                .andExpect(jsonPath("$.questions[0].chapterNo").value(1))
                .andExpect(jsonPath("$.questions[0].timeBudgetSec").value(120))      // exam.seconds.REMEMBER
                .andExpect(jsonPath("$.questions[0].rubricName").value("Template rubric"))
                .andExpect(jsonPath("$.questions[0].status").value("PENDING"));
        assertThat(jdbc.queryForObject("""
                select rubric_snapshot->>'rubricId' from attempt_questions where attempt_id = ?""", String.class, attempt))
                .as("the template rubric overrides the question's own (D45)").isEqualTo(templateRubric.toString());
        call(get("/api/v1/attempts/" + attempt), token(student)).andExpect(status().isNotFound());
        call(get("/api/v1/attempts/" + attempt), token(data.lecturer())).andExpect(status().isNotFound());
    }

    @Test
    void checkInRules() throws Exception {
        Course c = course();
        questions(c, c.chapterA(), UNDERSTAND, 3);
        User student = data.student();
        User outsider = data.student();
        UUID exam = createExam(c, template(c, 1), T0.plusSeconds(600), T0.plusSeconds(3600), null);
        addStudents(c, exam, student);
        publish(c, exam);

        checkIn(student, exam).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECKIN_NOT_OPEN"));
        clock.set(T0.plusSeconds(700));
        call(post("/api/v1/me/viva-exams/" + exam + "/check-in"), token(student), "{\"consentRecording\":false}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        checkIn(outsider, exam).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VIVA_EXAM_NOT_FOUND"));

        UUID attempt = checkedIn(student, exam);
        // a repeated click returns the running attempt instead of a second one
        assertThat(checkedIn(student, exam)).isEqualTo(attempt);
        data.attemptState(attempt, "COMPLETED", clock.instant().plus(Duration.ofMinutes(3)));
        checkIn(student, exam).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_ALREADY_USED"));
        assertThat(jdbc.queryForObject("select count(*) from exam_attempts where viva_exam_id = ?", Integer.class, exam)).isOne();
        call(delete("/api/v1/viva-exams/" + exam + "/students/" + student.getUserId()), c.token())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_EXISTS"));
    }

    @Test
    void poolThatShrankAfterPublishingFailsTheCheckInClearly() throws Exception {
        Course c = course();
        List<UUID> qs = questions(c, c.chapterA(), UNDERSTAND, 2);
        User student = data.student();
        UUID exam = openExam(c, template(c, 2), student);
        jdbc.update("update questions set status = 'DRAFT' where question_id = ?", qs.getFirst());
        checkIn(student, exam)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POOL_TOO_SMALL"));
        assertThat(jdbc.queryForObject("select count(*) from exam_attempts where viva_exam_id = ?", Integer.class, exam)).isZero();
    }

    @Test
    void retakeAvoidsTheQuestionsTheStudentAlreadyHad_AC_C5() throws Exception {
        Course c = course();
        questions(c, c.chapterA(), UNDERSTAND, 6);
        User student = data.student();
        UUID template = template(c, 3);
        UUID exam = openExam(c, template, student);
        UUID first = checkedIn(student, exam);
        data.attemptState(first, "COMPLETED", clock.instant().plusSeconds(300));

        String retake = JsonPath.read(json(call(post("/api/v1/viva-exams/" + exam + "/retake"), c.token(),
                "{\"checkinOpensAt\":\"" + T0 + "\",\"checkinClosesAt\":\"" + T0.plusSeconds(7200)
                        + "\",\"studentCodes\":\"" + student.getStudentCode() + "\"}")
                .andExpect(status().isCreated())), "$.exam.id");
        publish(c, UUID.fromString(retake));
        UUID second = checkedIn(student, UUID.fromString(retake));

        assertThat(attemptQuestions(second)).hasSize(3).doesNotContainAnyElementsOf(attemptQuestions(first));
        Map<UUID, Integer> seen = new HashMap<>();
        attemptQuestions(first).forEach(q -> seen.merge(q, 1, Integer::sum));
        attemptQuestions(second).forEach(q -> seen.merge(q, 1, Integer::sum));
        assertThat(seen).hasSize(6);
    }
}
