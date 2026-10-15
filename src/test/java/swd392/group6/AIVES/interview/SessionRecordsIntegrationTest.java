package swd392.group6.AIVES.interview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.grading.ExamFixtures;
import swd392.group6.AIVES.grading.ExamFixtures.CourseFx;
import swd392.group6.AIVES.grading.ExamFixtures.SessionFx;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lượt thi records (15 §5.4): turns, events, audio, per-role visibility. */
@IntegrationTest
class SessionRecordsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StoragePort storage;

    private ExamFixtures fx;
    private UUID examId;
    private SessionFx session;
    private UUID mainTurn;
    private UUID followUp;
    private String lecturerToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        fx = new ExamFixtures(jdbc, users);
        CourseFx course = fx.course();
        ExamFixtures.RubricFx rubric = fx.rubric(course, 10, 100);
        examId = fx.exam(course, 1);
        UUID q = fx.question(course, rubric.rubricId(), "Q");
        User student = fx.student("Student");
        session = fx.completedSession(examId, course, student, List.of(q));
        String audioKey = "sessions/" + session.sessionId() + "/turns/1.webm";
        mainTurn = fx.turn(session, 0, q, null, 0, "ANSWERED", "câu trả lời chính", 40, audioKey);
        followUp = fx.turn(session, 0, q, mainTurn, 1, "ANSWERED", "bổ sung", 20, null);
        storage.put(audioKey, new byte[] {1, 2, 3}, "audio/webm");
        fx.event(session.sessionId(), "SESSION_STARTED");
        fx.event(session.sessionId(), "SESSION_COMPLETED");
        lecturerToken = token(course.lecturer());
        studentToken = token(student);
    }

    private String token(User user) throws Exception {
        return "Bearer " + TestUsers.login(mockMvc, user.getUsername(), TestUsers.PASSWORD);
    }

    @Test
    void lecturerSeesOrderedTurnsWithAiAnalysisAndEvents() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/{id}/turns", session.sessionId()).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].turnId").value(mainTurn.toString()))
                .andExpect(jsonPath("$[0].type").value("MAIN"))
                .andExpect(jsonPath("$[0].threadOrderNo").value(1))
                .andExpect(jsonPath("$[0].transcript").value("câu trả lời chính"))
                .andExpect(jsonPath("$[0].aiAnalysis.coverage").value(0.5))
                .andExpect(jsonPath("$[0].followupDecision").value("NEXT_COMPLETE"))
                .andExpect(jsonPath("$[0].answerAudioUrl").value("/api/v1/turns/" + mainTurn + "/answer-audio"))
                .andExpect(jsonPath("$[1].turnId").value(followUp.toString()))
                .andExpect(jsonPath("$[1].followupIndex").value(1))
                .andExpect(jsonPath("$[1].answerAudioUrl").doesNotExist());

        mockMvc.perform(get("/api/v1/sessions/{id}/events", session.sessionId()).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].payload.note").exists());
    }

    @Test
    void audioIsStreamedOr404() throws Exception {
        mockMvc.perform(get("/api/v1/turns/{id}/answer-audio", mainTurn).header("Authorization", lecturerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/webm"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
        mockMvc.perform(get("/api/v1/turns/{id}/answer-audio", followUp).header("Authorization", lecturerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIO_NOT_FOUND"));
        // question audio key is set but nothing is stored under it
        mockMvc.perform(get("/api/v1/turns/{id}/question-audio", mainTurn).header("Authorization", studentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIO_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/turns/{id}/answer-audio", UUID.randomUUID()).header("Authorization", lecturerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TURN_NOT_FOUND"));
        // the student's own answer audio only after results are released
        mockMvc.perform(get("/api/v1/turns/{id}/answer-audio", mainTurn).header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        fx.releaseResults(examId, Instant.now());
        mockMvc.perform(get("/api/v1/turns/{id}/answer-audio", mainTurn).header("Authorization", studentToken))
                .andExpect(status().isOk());
    }

    @Test
    void visibilityPerRole() throws Exception {
        String outsider = token(users.create(Role.LECTURER));
        String otherStudent = token(fx.student("Other"));
        String admin = token(users.create(Role.ADMIN));

        mockMvc.perform(get("/api/v1/sessions/{id}/turns", session.sessionId()).header("Authorization", outsider))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sessions/{id}/events", session.sessionId()).header("Authorization", outsider))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/turns/{id}/answer-audio", mainTurn).header("Authorization", outsider))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sessions/{id}/turns", session.sessionId()).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sessions/{id}/events", session.sessionId()).header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/sessions/{id}/turns", session.sessionId()).header("Authorization", otherStudent))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/turns/{id}/question-audio", mainTurn).header("Authorization", otherStudent))
                .andExpect(status().isNotFound());

        // own student: not before release, then turns without AI analysis
        mockMvc.perform(get("/api/v1/sessions/{id}/turns", session.sessionId()).header("Authorization", studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESULTS_NOT_RELEASED"));
        fx.releaseResults(examId, Instant.now());
        mockMvc.perform(get("/api/v1/sessions/{id}/turns", session.sessionId()).header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].transcript").value("câu trả lời chính"))
                .andExpect(jsonPath("$[0].aiAnalysis").doesNotExist())
                .andExpect(jsonPath("$[0].followupDecision").doesNotExist());
        mockMvc.perform(get("/api/v1/sessions/{id}/turns", UUID.randomUUID()).header("Authorization", lecturerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }
}
