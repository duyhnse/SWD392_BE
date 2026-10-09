package swd392.group6.AIVES.interview.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.exam.AttemptStartedEvent;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

/** Writes {@code attempt_events} rows for what other modules report (FG5 audit trail, BR-I5). */
@Component
@RequiredArgsConstructor
class AttemptEventLog {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;

    /** Same transaction as the check-in: no attempt without its CHECKED_IN event. */
    @EventListener
    void on(AttemptStartedEvent e) {
        jdbc.update("""
                        insert into attempt_events (event_id, attempt_id, turn_id, type, actor_id, payload, created_at)
                        values (?, ?, null, 'CHECKED_IN', ?, cast(? as jsonb), ?)""",
                UUID.randomUUID(), e.attemptId(), e.studentId(),
                JSON.writeValueAsString(Map.of("selectionSeed", e.selectionSeed(), "warnings", e.warnings())),
                Timestamp.from(e.startedAt()));
    }
}
