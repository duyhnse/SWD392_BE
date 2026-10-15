package swd392.group6.AIVES.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Asynchronous AI work (contract 16 §4): a job row in {@code ai_jobs} is written in the caller's transaction, handed
 * to the AI node after commit, and finished by the node's signed callback — or by polling / timeout in
 * {@link #sweep()}. Every finish publishes one {@link AiJobFinishedEvent}.
 */
@Slf4j
@Service
public class AiJobService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_SUBMITS = 5;

    private final JdbcTemplate jdbc;
    private final AiJobPort port;
    private final AiNodeProperties properties;
    private final ApplicationEventPublisher events;
    /** REQUIRES_NEW: {@link #dispatch} runs from afterCommit, where the caller's transaction is already over. */
    private final TransactionTemplate tx;
    private final Clock clock;

    public AiJobService(JdbcTemplate jdbc, AiJobPort port, AiNodeProperties properties, ApplicationEventPublisher events,
                        PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.port = port;
        this.properties = properties;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public record AiJob(UUID jobId, AiJobType type, String subjectType, UUID subjectId, String status, JsonNode request,
                        JsonNode result, String errorCode, String errorMessage, Instant createdAt, Instant finishedAt) {
    }

    /** Must run inside a transaction; the job is sent once that transaction commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(AiJobType type, String subjectType, UUID subjectId, Object input) {
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
                        insert into ai_jobs (job_id, job_type, subject_type, subject_id, status, request, created_at)
                        values (?, ?, ?, ?, 'QUEUED', cast(? as jsonb), ?)""",
                jobId, type.name(), subjectType, subjectId, JSON.writeValueAsString(input), Timestamp.from(clock.instant()));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(jobId);
            }
        });
        return jobId;
    }

    @Transactional(readOnly = true)
    public Optional<AiJob> find(UUID jobId) {
        return jdbc.query("""
                        select job_id, job_type, subject_type, subject_id, status, request::text, result::text, error_code,
                               error_message, created_at, finished_at from ai_jobs where job_id = ?""",
                (rs, i) -> new AiJob(rs.getObject(1, UUID.class), AiJobType.valueOf(rs.getString(2)), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), parse(rs.getString(6)), parse(rs.getString(7)),
                        rs.getString(8), rs.getString(9), instant(rs.getTimestamp(10)), instant(rs.getTimestamp(11))),
                jobId).stream().findFirst();
    }

    /** Sends a QUEUED job; failures leave it QUEUED for the sweeper (at most {@value #MAX_SUBMITS} tries). */
    public void dispatch(UUID jobId) {
        Optional<AiJob> job = tx.execute(s -> find(jobId)).filter(j -> "QUEUED".equals(j.status()));
        if (job.isEmpty()) {
            return;
        }
        Optional<AiJobPort.AiJobOutcome> immediate;
        try {
            immediate = port.submit(jobId, job.get().type(), job.get().request(), properties.callbackUrl(jobId));
        } catch (AiUnavailableException e) {
            int tries = tx.execute(s -> jdbc.queryForObject(
                    "update ai_jobs set submit_attempts = submit_attempts + 1 where job_id = ? returning submit_attempts",
                    Integer.class, jobId));
            log.warn("AI job {} ({}) not accepted, try {}: {}", jobId, job.get().type(), tries, e.getMessage());
            if (tries >= MAX_SUBMITS) {
                complete(jobId, new AiJobPort.AiJobOutcome("FAILED", null, "AI_NODE_UNAVAILABLE", e.getMessage()));
            }
            return;
        }
        tx.executeWithoutResult(s -> jdbc.update("""
                update ai_jobs set status = 'SUBMITTED', submitted_at = ?, submit_attempts = submit_attempts + 1
                where job_id = ? and status = 'QUEUED'""", Timestamp.from(clock.instant()), jobId));
        immediate.ifPresent(outcome -> complete(jobId, outcome));
    }

    /**
     * Stores a result (callback, poll or immediate). Idempotent: a job that already finished keeps its first result.
     *
     * @return false when the job is unknown or already finished
     */
    public boolean complete(UUID jobId, AiJobPort.AiJobOutcome outcome) {
        String status = switch (outcome.status()) {
            case "SUCCEEDED", "FAILED", "TIMED_OUT" -> outcome.status();
            default -> throw new IllegalArgumentException("Not a final job status: " + outcome.status());
        };
        Optional<AiJob> finished = tx.execute(s -> {
            int updated = jdbc.update("""
                            update ai_jobs set status = ?, result = cast(? as jsonb), error_code = ?, error_message = ?,
                                               finished_at = ?
                            where job_id = ? and status in ('QUEUED', 'SUBMITTED')""",
                    status, outcome.result() == null ? null : JSON.writeValueAsString(outcome.result()),
                    outcome.errorCode(), truncate(outcome.errorMessage()), Timestamp.from(clock.instant()), jobId);
            return updated == 0 ? Optional.<AiJob>empty() : find(jobId);
        });
        finished.ifPresent(j -> events.publishEvent(
                new AiJobFinishedEvent(j.jobId(), j.type(), j.subjectType(), j.subjectId(), j.status())));
        return finished.isPresent();
    }

    /** Re-sends QUEUED jobs, polls SUBMITTED ones that are late, times out the ones that never come back. */
    public void sweep() {
        Instant now = clock.instant();
        List<UUID> queued = jdbc.queryForList(
                "select job_id from ai_jobs where status = 'QUEUED' and created_at < ? order by created_at limit 50",
                UUID.class, Timestamp.from(now.minusSeconds(30)));
        queued.forEach(this::dispatch);
        List<Object[]> late = jdbc.query("""
                        select job_id, submitted_at from ai_jobs where status = 'SUBMITTED' and submitted_at < ?
                        order by submitted_at limit 50""",
                (rs, i) -> new Object[]{rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()},
                Timestamp.from(now.minus(properties.jobTimeout())));
        for (Object[] row : late) {
            UUID jobId = (UUID) row[0];
            Optional<AiJobPort.AiJobOutcome> polled = Optional.empty();
            try {
                polled = port.poll(jobId);
            } catch (AiUnavailableException e) {
                log.warn("Polling AI job {} failed: {}", jobId, e.getMessage());
            }
            if (polled.isPresent()) {
                complete(jobId, polled.get());
            } else if (((Instant) row[1]).isBefore(now.minus(properties.jobTimeout().multipliedBy(3)))) {
                complete(jobId, new AiJobPort.AiJobOutcome("TIMED_OUT", null, "AI_JOB_TIMEOUT",
                        "No result from the AI node in time"));
            }
        }
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 2000 ? s : s.substring(0, 2000);
    }

    private static JsonNode parse(String json) {
        return json == null ? null : JSON.readTree(json);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
