package swd392.group6.AIVES.exam;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import swd392.group6.AIVES.interview.AttemptCompletedEvent;
import swd392.group6.AIVES.support.FakeAiNode;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.user.User;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** WF3 runtime (05 §4–§8, AC-I1…AC-I17): the turn loop over the fake AI node, timing, disconnects, examiner control. */
@IntegrationTest
@Import({ExamTestConfiguration.class, InterviewRuntimeIntegrationTest.Events.class,
        swd392.group6.AIVES.interview.internal.InterviewTestHooks.class})
class InterviewRuntimeIntegrationTest extends ExamTestBase {

    private static final String LONG = "Modular monolith là một khối triển khai duy nhất nhưng chia module theo nghiệp vụ, "
            + "mỗi module sở hữu dữ liệu riêng và giao tiếp qua interface rõ ràng nên dễ bảo trì";
    private static final String SHORT = "Là chia module";

    @Autowired FakeAiNode aiNode;
    @Autowired swd392.group6.AIVES.interview.internal.InterviewTestHooks hooks;
    @Autowired Events completed;

    /** Collects completion events (BR-I9). */
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class Events {
        final List<AttemptCompletedEvent> seen = new java.util.concurrent.CopyOnWriteArrayList<>();

        @org.springframework.context.event.EventListener
        void on(AttemptCompletedEvent e) {
            seen.add(e);
        }
    }

    @BeforeEach
    void reset() {
        clock.set(T0);
        aiNode.ttsBroken = false;
        aiNode.answerRequests.clear();
    }

    private record Started(Course c, UUID exam, User student, UUID attempt) {
    }

    /** n questions × {@code seconds}, check-in open now, one student checked in. */
    private Started start(int n, int seconds, String templateExtra) throws Exception {
        Course c = course();
        questions(c, c.topicA(), swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND, n + 2);
        UUID template = template(c, "[{\"count\":" + n + ",\"secondsPerQuestion\":" + seconds + "}]", templateExtra);
        UUID exam = createExam(c, template, T0, T0.plusSeconds(7200), null);
        User student = data.student();
        addStudents(c, exam, student);
        publish(c, exam);
        return new Started(c, exam, student, checkedIn(student, exam));
    }

    private Started start(int n) throws Exception {
        return start(n, 300, "\"maxFollowupsPerQuestion\":2");
    }

    private String state(Started s) throws Exception {
        return json(call(get("/api/v1/attempts/" + s.attempt() + "/state"), token(s.student())).andExpect(status().isOk()));
    }

    private ResultActions answer(Started s, String turnId, String text) throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "a.webm", "audio/webm", text.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/v1/attempts/" + s.attempt() + "/turns/" + turnId + "/answer").file(audio)
                .param("durationSec", "20").header("Authorization", "Bearer " + token(s.student())));
    }

    private String answered(Started s, String turnId, String text) throws Exception {
        return json(answer(s, turnId, text).andExpect(status().isOk()));
    }

    private static String turnId(String state) {
        return JsonPath.read(state, "$.currentTurn.turnId");
    }

    private List<String> eventTypes(UUID attempt) {
        return jdbc.queryForList("select type from attempt_events where attempt_id = ? order by created_at, type", String.class, attempt);
    }

    private String attemptStatus(UUID attempt) {
        return jdbc.queryForObject("select status || '/' || coalesce(end_reason, '') from exam_attempts where attempt_id = ?",
                String.class, attempt);
    }

    @Test
    void firstStateAsksTheFirstMainQuestionWithItsAudio_AC_I1() throws Exception {
        Started s = start(2);
        String st = state(s);
        assertThat((String) JsonPath.read(st, "$.status")).isEqualTo("IN_PROGRESS");
        assertThat((String) JsonPath.read(st, "$.currentTurn.type")).isEqualTo("MAIN");
        assertThat((String) JsonPath.read(st, "$.currentTurn.audioUrl")).endsWith("/question-audio");
        assertThat((Integer) JsonPath.read(st, "$.progress.threadIndex")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(st, "$.progress.threadCount")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(st, "$.remainingSec")).isEqualTo(600);
        assertThat(state(s)).contains(turnId(st)); // polling again does not create another turn
        call(get(JsonPath.<String>read(st, "$.currentTurn.audioUrl")), token(s.student())).andExpect(status().isOk());
    }

    @Test
    void shortAnswerGetsAFollowUpThenTheThreadMovesOn_AC_I2() throws Exception {
        Started s = start(2);
        String main = turnId(state(s));
        String afterShort = answered(s, main, SHORT);
        assertThat((String) JsonPath.read(afterShort, "$.currentTurn.type")).isEqualTo("FOLLOW_UP");
        assertThat((Integer) JsonPath.read(afterShort, "$.progress.followupIndex")).isEqualTo(1);
        UUID followUp = UUID.fromString(turnId(afterShort));
        assertThat(jdbc.queryForObject("select parent_turn_id from exam_turns where turn_id = ?", UUID.class, followUp))
                .isEqualTo(UUID.fromString(main));
        String afterLong = answered(s, followUp.toString(), LONG);
        assertThat((String) JsonPath.read(afterLong, "$.currentTurn.type")).isEqualTo("MAIN");
        assertThat((Integer) JsonPath.read(afterLong, "$.progress.threadIndex")).isEqualTo(2);
        // the follow-up analysis saw the main answer of the same thread
        assertThat(aiNode.answerRequests.getLast().priorTurns()).hasSize(1);
    }

    @Test
    void followUpLimitIsNeverExceededAndSkipsTheAnalysis_AC_I3_AC_I5() throws Exception {
        Started s = start(1, 300, "\"maxFollowupsPerQuestion\":1");
        String t1 = turnId(state(s));
        String t2 = turnId(answered(s, t1, SHORT));
        int calls = aiNode.answerRequests.size();
        String done = answered(s, t2, SHORT);
        assertThat(aiNode.answerRequests).hasSize(calls); // NEXT forced → only STT, no analysis
        assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select followup_decision from exam_turns where turn_id = ?", String.class,
                UUID.fromString(t2))).isEqualTo("NEXT_LIMIT");
    }

    @Test
    void lowRemainingTimeForcesNextWithoutAnalysis_AC_I5() throws Exception {
        Started s = start(1, 40, "\"maxFollowupsPerQuestion\":2");
        String t1 = turnId(state(s));
        clock.advance(Duration.ofSeconds(20)); // 20 of 40 s used → < 30 s left
        String done = answered(s, t1, SHORT);
        assertThat(aiNode.answerRequests).isEmpty();
        assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select followup_decision from exam_turns where turn_id = ?", String.class,
                UUID.fromString(t1))).isEqualTo("NEXT_TIME");
    }

    @Test
    void doNotKnowMovesToTheNextQuestion_AC_I4() throws Exception {
        Started s = start(2);
        String t1 = turnId(state(s));
        String next = answered(s, t1, "Dạ em không biết ạ, phần này em chưa học kỹ lắm thầy ơi");
        assertThat((String) JsonPath.read(next, "$.currentTurn.type")).isEqualTo("MAIN");
        assertThat(jdbc.queryForObject("select followup_decision from exam_turns where turn_id = ?", String.class,
                UUID.fromString(t1))).isEqualTo("NEXT_WRONG");
    }

    @Test
    void allQuestionsDoneCompletesOncePublishesTheEvent_AC_I15_BR_I9() throws Exception {
        Started s = start(2);
        String t = turnId(state(s));
        t = turnId(answered(s, t, LONG));
        String done = answered(s, t, LONG);
        assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("COMPLETED");
        assertThat(attemptStatus(s.attempt())).isEqualTo("COMPLETED/ALL_QUESTIONS_DONE");
        assertThat(completed.seen.stream().filter(e -> e.attemptId().equals(s.attempt()))).hasSize(1);
        assertThat(eventTypes(s.attempt())).contains("CHECKED_IN", "QUESTION_ASKED", "ANSWER_SUBMITTED",
                "ANSWER_PROCESSED", "ATTEMPT_COMPLETED");
        assertThat(jdbc.queryForList("select status from attempt_questions where attempt_id = ?", String.class, s.attempt()))
                .containsOnly("DONE");
        answer(s, t, LONG).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_NOT_IN_PROGRESS"));
    }

    @Test
    void deadlinePassedEndsWithTimeUp_AC_I6() throws Exception {
        Started s = start(3);
        state(s);
        clock.advance(Duration.ofSeconds(901));
        hooks.sweep();
        assertThat(attemptStatus(s.attempt())).isEqualTo("COMPLETED/TIME_UP");
        assertThat(jdbc.queryForList("select status from attempt_questions where attempt_id = ? order by order_no",
                String.class, s.attempt())).containsExactly("NOT_REACHED", "NOT_REACHED", "NOT_REACHED");
        assertThat(eventTypes(s.attempt())).contains("TIME_UP");
    }

    @Test
    void sttFailureMarksTheTurnAndTheExamGoesOn_AC_I7() throws Exception {
        Started s = start(2);
        String t1 = turnId(state(s));
        String next = answered(s, t1, "#stt-fail");
        assertThat((Integer) JsonPath.read(next, "$.progress.threadIndex")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from exam_turns where turn_id = ?", String.class, UUID.fromString(t1)))
                .isEqualTo("TRANSCRIPTION_FAILED");
        assertThat(eventTypes(s.attempt())).contains("STT_FAILED");
    }

    @Test
    void analysisFailureKeepsTheTranscriptAndMovesOn_AC_I8() throws Exception {
        Started s = start(2);
        String t1 = turnId(state(s));
        answered(s, t1, "#ai-fail nhưng em vẫn trả lời đầy đủ về module");
        assertThat(jdbc.queryForObject("select followup_decision from exam_turns where turn_id = ?", String.class,
                UUID.fromString(t1))).isEqualTo("NEXT_AI_ERROR");
        assertThat(jdbc.queryForObject("select student_transcript from exam_turns where turn_id = ?", String.class,
                UUID.fromString(t1))).contains("module");
        assertThat(eventTypes(s.attempt())).contains("LLM_FAILED");
    }

    @Test
    void ttsFailureFallsBackToBrowserSpeech_AC_I9() throws Exception {
        aiNode.ttsBroken = true;
        Started s = start(1);
        String st = state(s);
        assertThat((Object) JsonPath.read(st, "$.currentTurn.audioUrl")).isNull();
        assertThat((String) JsonPath.read(st, "$.currentTurn.text")).isNotBlank();
        assertThat(eventTypes(s.attempt())).contains("TTS_FAILED");
    }

    @Test
    void examinerSkipShowsANoticeAndRejectsTheOldTurn_AC_I10() throws Exception {
        Started s = start(2);
        String t1 = turnId(state(s));
        call(post("/api/v1/attempts/" + s.attempt() + "/skip-current"), s.c().token(), "{\"reason\":\"AI hỏi lạc đề\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.threadIndex").value(2));
        String st = state(s);
        assertThat((String) JsonPath.read(st, "$.notice.type")).isEqualTo("LECTURER_SKIPPED");
        assertThat(turnId(st)).isNotEqualTo(t1);
        answer(s, t1, LONG).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TURN_NOT_CURRENT"));
        assertThat(jdbc.queryForObject("select status from attempt_questions where attempt_id = ? and order_no = 1",
                String.class, s.attempt())).isEqualTo("SKIPPED");
    }

    @Test
    void examinerEndNeedsAReasonAndSkipsTheRest_AC_I11() throws Exception {
        Started s = start(3);
        String t1 = turnId(state(s));
        answered(s, t1, LONG);
        call(post("/api/v1/attempts/" + s.attempt() + "/end"), s.c().token(), "{}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        call(post("/api/v1/attempts/" + s.attempt() + "/end"), s.c().token(), "{\"reason\":\"Sinh viên xin dừng\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(attemptStatus(s.attempt())).isEqualTo("COMPLETED/ENDED_BY_LECTURER");
        assertThat(jdbc.queryForList("select status from attempt_questions where attempt_id = ? order by order_no",
                String.class, s.attempt())).containsExactly("DONE", "SKIPPED", "SKIPPED");
    }

    @Test
    void longOutageInterruptsAndResumeGivesTheTimeBack_AC_I12() throws Exception {
        Started s = start(2);
        String t1 = turnId(state(s));
        Instant deadline = deadline(s.attempt());
        clock.advance(Duration.ofSeconds(15));
        hooks.sweep(); // offline since the last heartbeat
        assertThat(eventTypes(s.attempt())).contains("DISCONNECTED");
        clock.advance(Duration.ofSeconds(60));
        hooks.sweep(); // 75 s offline > 60 s grace
        assertThat(attemptStatus(s.attempt())).isEqualTo("INTERRUPTED/");
        assertThat(jdbc.queryForObject("select count(*) from notifications where type = 'ATTEMPT_INTERRUPTED' and user_id = ?",
                Integer.class, s.c().lecturer().getUserId())).isEqualTo(1);
        assertThat((String) JsonPath.read(state(s), "$.status")).isEqualTo("INTERRUPTED");

        clock.advance(Duration.ofSeconds(30));
        call(post("/api/v1/attempts/" + s.attempt() + "/resume"), s.c().token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        assertThat(deadline(s.attempt())).isEqualTo(deadline.plusSeconds(105)); // frozen since the last heartbeat
        String st = state(s);
        assertThat(turnId(st)).isEqualTo(t1);
        assertThat((String) JsonPath.read(st, "$.currentTurn.askedAt")).isEqualTo(clock.instant().toString());
    }

    @Test
    void tooManyShortOutagesInterrupt_AC_I12() throws Exception {
        Started s = start(2);
        state(s);
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofSeconds(12));
            state(s); // came back after 12 s each time
        }
        assertThat(attemptStatus(s.attempt())).isEqualTo("INTERRUPTED/");
    }

    @Test
    void shortOutageReasksTheSameQuestion_D51() throws Exception {
        Started s = start(2);
        String t1 = turnId(state(s));
        clock.advance(Duration.ofSeconds(15));
        String st = state(s);
        assertThat(turnId(st)).isEqualTo(t1);
        assertThat(jdbc.queryForObject("select disconnect_count from exam_attempts where attempt_id = ?", Integer.class,
                s.attempt())).isEqualTo(1);
        assertThat((String) JsonPath.read(st, "$.notice.type")).isEqualTo("RECONNECTED");
    }

    @Test
    void outageLongerThan20sDuringAMainQuestionReplacesIt_AC_I16() throws Exception {
        Started s = start(1);
        String t1 = turnId(state(s));
        UUID before = attemptQuestions(s.attempt()).getFirst();
        clock.advance(Duration.ofSeconds(35));
        String st = state(s);
        assertThat(turnId(st)).isNotEqualTo(t1);
        assertThat(attemptQuestions(s.attempt())).hasSize(1).doesNotContain(before);
        assertThat(jdbc.queryForObject("select void_reason from attempt_questions where attempt_id = ? and status = 'VOIDED'",
                String.class, s.attempt())).isEqualTo("REPLACED_AFTER_DISCONNECT");
        assertThat(eventTypes(s.attempt())).contains("QUESTION_REPLACED");
    }

    @Test
    void playbackAndProcessingAreNotCharged_AC_I17() throws Exception {
        Started s = start(1);
        String t1 = turnId(state(s));
        clock.advance(Duration.ofSeconds(12)); // 2 s question audio (fake TTS) + 10 s answering
        answered(s, t1, LONG);
        assertThat(jdbc.queryForObject("select answer_counted_ms from exam_turns where turn_id = ?", Integer.class,
                UUID.fromString(t1))).isEqualTo(10_000);
        assertThat(jdbc.queryForObject("select time_used_sec from attempt_questions where attempt_id = ?", Integer.class,
                s.attempt())).isEqualTo(10);
    }

    @Test
    void repostingTheSameAnswerIsIdempotent_AC_I13() throws Exception {
        Started s = start(2);
        String t1 = turnId(state(s));
        answered(s, t1, LONG);
        answered(s, t1, LONG);
        assertThat(jdbc.queryForObject("select count(*) from exam_turns where attempt_id = ?", Integer.class, s.attempt()))
                .isEqualTo(2);
    }

    @Test
    void anotherStudentCannotSeeTheAttempt_AC_I14() throws Exception {
        Started s = start(1);
        User other = data.student();
        call(get("/api/v1/attempts/" + s.attempt() + "/state"), token(other)).andExpect(status().isNotFound());
        call(get("/api/v1/attempts/" + s.attempt() + "/state"), s.c().token()).andExpect(status().isForbidden());
        User outsider = data.lecturer();
        call(post("/api/v1/attempts/" + s.attempt() + "/skip-current"), token(outsider), "{}").andExpect(status().isNotFound());
    }

    @Test
    void silenceAndOverdueAnswersBecomeNoAnswer_EX1() throws Exception {
        Started s = start(3);
        String t1 = turnId(state(s));
        String next = json(call(post("/api/v1/attempts/" + s.attempt() + "/turns/" + t1 + "/no-answer"), token(s.student()),
                "{\"reason\":\"SILENCE\"}").andExpect(status().isOk()));
        assertThat((Integer) JsonPath.read(next, "$.progress.threadIndex")).isEqualTo(2);
        String t2 = turnId(next);
        state(s);
        // the client stops reporting answers but keeps polling: the sweeper closes the overdue turn
        for (int i = 0; i < 15; i++) {
            clock.advance(Duration.ofSeconds(10));
            state(s);
        }
        hooks.sweep();
        assertThat(jdbc.queryForObject("select status from exam_turns where turn_id = ?", String.class, UUID.fromString(t2)))
                .isEqualTo("NO_ANSWER");
    }

    @Test
    void liveMonitorShowsProgressAndClientEventsAreLogged() throws Exception {
        Started s = start(2);
        state(s);
        call(post("/api/v1/attempts/" + s.attempt() + "/client-events"), token(s.student()),
                "{\"type\":\"TAB_HIDDEN\",\"details\":{\"ms\":1200}}").andExpect(status().isNoContent());
        call(post("/api/v1/attempts/" + s.attempt() + "/client-events"), token(s.student()), "{\"type\":\"HACK\"}")
                .andExpect(status().isUnprocessableContent());
        call(get("/api/v1/viva-exams/" + s.exam() + "/live"), s.c().token()).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptId").value(s.attempt().toString()))
                .andExpect(jsonPath("$[0].online").value(true))
                .andExpect(jsonPath("$[0].threadIndex").value(1))
                .andExpect(jsonPath("$[0].currentTurnType").value("MAIN"));
        assertThat(eventTypes(s.attempt())).contains("TAB_HIDDEN");
        String events = json(call(get("/api/v1/attempts/" + s.attempt() + "/events"), s.c().token()).andExpect(status().isOk()));
        assertThat(JsonPath.<List<Integer>>read(events, "$[?(@.type == 'TAB_HIDDEN')].payload.ms")).containsExactly(1200);
    }

    private Instant deadline(UUID attempt) {
        return jdbc.queryForObject("select deadline_at from exam_attempts where attempt_id = ?", java.sql.Timestamp.class,
                attempt).toInstant();
    }
}
