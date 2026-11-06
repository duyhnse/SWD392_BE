package swd392.group6.AIVES.interview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import swd392.group6.AIVES.grading.ExamFixtures;
import swd392.group6.AIVES.grading.ExamFixtures.SessionFx;
import swd392.group6.AIVES.grading.GradingApi;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** AIV-29 recording retention: media of old, graded attempts go; evidence still needed stays. */
@IntegrationTest
class MediaRetentionIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestUsers users;
    @Autowired private StoragePort storage;
    @Autowired private GradingApi grading;
    @Autowired private MediaRetention retention;

    private ExamFixtures fx;
    private ExamFixtures.CourseFx course;
    private ExamFixtures.RubricFx rubric;
    private UUID examId;

    @BeforeEach
    void setUp() {
        fx = new ExamFixtures(jdbc, users);
        course = fx.course();
        rubric = fx.rubric(course, 10, 100);
        examId = fx.exam(course, 1);
    }

    /** A finished attempt with one camera chunk and one answer audio, ended `daysAgo` days ago, evaluation in `status`. */
    private String[] attempt(int daysAgo, String evaluationStatus) {
        UUID q = fx.question(course, rubric.rubricId(), "Q " + ExamFixtures.unique());
        SessionFx s = fx.completedSession(examId, course, fx.student("R"), List.of(q));
        String answer = "answers/" + UUID.randomUUID() + ".webm", chunk = "recordings/" + UUID.randomUUID() + ".webm";
        storage.put(answer, new byte[] {1}, "audio/webm");
        storage.put(chunk, new byte[] {2}, "video/webm");
        fx.turn(s, 0, q, null, 0, "ANSWERED", "câu trả lời", 20, answer);
        jdbc.update("""
                insert into attempt_recordings (recording_id, attempt_id, kind, chunk_index, storage_key, content_type, size_bytes, sha256)
                values (?, ?, 'CAMERA', 0, ?, 'video/webm', 1, 'x')""", UUID.randomUUID(), s.sessionId(), chunk);
        jdbc.update("update exam_attempts set ended_at = ? where attempt_id = ?",
                Timestamp.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS)), s.sessionId());
        if (evaluationStatus != null) {
            grading.createEvaluation(s.sessionId(), GradingApi.Mode.MANUAL);
            jdbc.update("update grade_evaluations set status = ? where attempt_id = ?", evaluationStatus, s.sessionId());
        }
        return new String[] {s.sessionId().toString(), answer, chunk};
    }

    @Test
    void oldGradedAttemptsLoseTheirMediaOnly() {
        String[] old = attempt(400, "CONFIRMED");
        String[] recent = attempt(10, "CONFIRMED");
        String[] ungraded = attempt(400, "AWAITING_REVIEW");
        String[] disputed = attempt(400, "CONFIRMED");
        UUID evaluation = jdbc.queryForObject("select evaluation_id from grade_evaluations where attempt_id = ?::uuid", UUID.class, disputed[0]);
        UUID student = jdbc.queryForObject("select student_id from exam_attempts where attempt_id = ?::uuid", UUID.class, disputed[0]);
        jdbc.update("insert into grade_disputes (dispute_id, evaluation_id, student_id, reason) values (?, ?, ?, 'xin xem lại')",
                UUID.randomUUID(), evaluation, student);

        retention.purge();

        assertThat(storage.exists(old[1])).isFalse();
        assertThat(storage.exists(old[2])).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from attempt_recordings where attempt_id = ?::uuid", Integer.class, old[0])).isZero();
        assertThat(jdbc.queryForObject("select student_transcript from exam_turns where attempt_id = ?::uuid", String.class, old[0]))
                .isEqualTo("câu trả lời");                                            // the transcript stays
        assertThat(jdbc.queryForObject("select count(*) from attempt_events where attempt_id = ?::uuid and type = 'MEDIA_PURGED'",
                Integer.class, old[0])).isEqualTo(1);
        for (String[] kept : List.of(recent, ungraded, disputed)) {
            assertThat(storage.exists(kept[1])).isTrue();
            assertThat(storage.exists(kept[2])).isTrue();
        }
        assertThat(retention.purge()).isZero();                                       // nothing left to do
    }
}
