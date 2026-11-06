package swd392.group6.AIVES.interview.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.exam.AttemptStartedEvent;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Writes {@code attempt_events} rows: every system, student and lecturer action of an attempt (FG5 audit trail, BR-I5). */
@Component
@RequiredArgsConstructor
public class AttemptEventLog {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;

    /** Same transaction as the check-in: no attempt without its CHECKED_IN event. */
    @EventListener
    void on(AttemptStartedEvent e) {
        jdbc.update("""
                        insert into attempt_events (event_id, attempt_id, turn_id, type, actor_id, payload, created_at)
                        values (?, ?, null, 'CHECKED_IN', ?, cast(? as jsonb), ?)""",
                UUID.randomUUID(), e.attemptId(), e.studentId(),
                JSON.writeValueAsString(Map.of("selectionSeed", e.selectionSeed())),
                Timestamp.from(e.startedAt()));
    }

    /**
     * @param actorId null = system
     * @param payload JSON-serialisable details, may be null
     */
    void log(UUID attemptId, UUID turnId, String type, UUID actorId, Map<String, ?> payload, Instant at) {
        jdbc.update("""
                        insert into attempt_events (event_id, attempt_id, turn_id, type, actor_id, payload, created_at)
                        values (?, ?, ?, ?, ?, cast(? as jsonb), ?)""",
                UUID.randomUUID(), attemptId, turnId, type, actorId,
                payload == null ? null : JSON.writeValueAsString(payload), Timestamp.from(at));
    }

    /** Number of events = the attempt's state version: every visible change writes one (05 §7). */
    int version(UUID attemptId) {
        Integer n = jdbc.queryForObject("select count(*) from attempt_events where attempt_id = ?", Integer.class, attemptId);
        return n == null ? 0 : n;
    }

    record Notice(String type, Instant at) {
    }

    /** The latest event the student should be told about ("the examiner skipped this question"), if recent. */
    Notice notice(UUID attemptId, Instant now) {
        return jdbc.query("""
                        select type, created_at from attempt_events where attempt_id = ? and type in (%s)
                        order by created_at desc limit 1""".formatted(NOTICES),
                        (rs, i) -> new Notice(rs.getString(1), rs.getTimestamp(2).toInstant()), attemptId).stream()
                .filter(n -> n.at().isAfter(now.minusSeconds(60))).findFirst().orElse(null);
    }

    private static final String NOTICES = "'LECTURER_SKIPPED', 'LECTURER_RESUMED', 'LECTURER_ENDED', 'LECTURER_VOIDED', 'QUESTION_REPLACED',"
            + " 'TIME_UP', 'INTERRUPTED', 'RECONNECTED'";
}
