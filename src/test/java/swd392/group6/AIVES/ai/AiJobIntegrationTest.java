package swd392.group6.AIVES.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import swd392.group6.AIVES.ai.node.AiNodeFileLinks;
import swd392.group6.AIVES.ai.node.AiNodeSignature;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.support.IntegrationTest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Asynchronous AI jobs (16 §4): queue, dispatch after commit, signed callbacks, file links. */
@IntegrationTest
@RecordApplicationEvents
class AiJobIntegrationTest {

    private static final String SECRET = "test-callback-secret";

    @Autowired private AiJobService jobs;
    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;
    @Autowired private AiNodeFileLinks fileLinks;
    @Autowired private StoragePort storage;

    @Test
    void jobIsSentAfterCommitAndTheMockFinishesItAtOnce() {
        UUID subject = UUID.randomUUID();
        Map<String, Object> input = Map.of(
                "rubric", Map.of("criteria", List.of(Map.of("criterion_id", "c1", "max_score", 10))),
                "turns", List.of(Map.of("transcript", "một hai ba bốn năm sáu bảy tám chín mười")));
        UUID jobId = tx.execute(s -> {
            UUID id = jobs.enqueue(AiJobType.GRADE_THREAD, "QUESTION_GRADE", subject, input);
            assertThat(jobs.find(id).orElseThrow().status()).isEqualTo("QUEUED");
            return id;
        });

        AiJobService.AiJob job = jobs.find(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo("SUCCEEDED");
        assertThat(job.result().path("criteria").get(0).path("score").asDouble()).isEqualTo(1.25); // 10 words / 80 × 10
        assertThat(job.finishedAt()).isNotNull();
        assertThat(events.stream(AiJobFinishedEvent.class))
                .anySatisfy(e -> {
                    assertThat(e.jobId()).isEqualTo(jobId);
                    assertThat(e.subjectId()).isEqualTo(subject);
                    assertThat(e.status()).isEqualTo("SUCCEEDED");
                });
    }

    @Test
    void enqueueOutsideATransactionIsAProgrammingError() {
        assertThatThrownBy(
                        () -> jobs.enqueue(AiJobType.SUMMARIZE_EVALUATION, "EVALUATION", UUID.randomUUID(), Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void signedCallbackFinishesASubmittedJobOnce() throws Exception {
        UUID jobId = submittedJob();
        String body = "{\"job_id\":\"" + jobId + "\",\"type\":\"GRADE_THREAD\",\"status\":\"SUCCEEDED\",\"result\":{\"feedback\":\"ok\"}}";

        mockMvc.perform(post("/internal/ai-node/jobs/{id}/result", jobId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        callback(jobId, body.replace("ok", "tampered"), sign(body)).andExpect(status().isUnauthorized());

        callback(jobId, body, sign(body)).andExpect(status().isNoContent());
        assertThat(jobs.find(jobId).orElseThrow().result().path("feedback").asString()).isEqualTo("ok");

        // a repeated or late callback does not overwrite the first result
        String failed = "{\"job_id\":\"" + jobId + "\",\"status\":\"FAILED\",\"error\":{\"code\":\"X\",\"message\":\"late\"}}";
        callback(jobId, failed, sign(failed)).andExpect(status().isNoContent());
        assertThat(jobs.find(jobId).orElseThrow().status()).isEqualTo("SUCCEEDED");
        assertThat(events.stream(AiJobFinishedEvent.class).filter(e -> e.jobId().equals(jobId)).count()).isOne();

        String unknown = "{\"status\":\"SUCCEEDED\",\"result\":{}}";
        callback(UUID.randomUUID(), unknown, sign(unknown)).andExpect(status().isNotFound());
        String running = "{\"status\":\"RUNNING\"}";
        callback(jobId, running, sign(running)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void failedCallbackKeepsTheErrorForTheConsumer() throws Exception {
        UUID jobId = submittedJob();
        String body = "{\"status\":\"FAILED\",\"error\":{\"code\":\"LLM_INVALID_OUTPUT\",\"message\":\"twice invalid JSON\"}}";
        callback(jobId, body, sign(body)).andExpect(status().isNoContent());
        AiJobService.AiJob job = jobs.find(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo("FAILED");
        assertThat(job.errorCode()).isEqualTo("LLM_INVALID_OUTPUT");
        assertThat(job.result()).isNull();
    }

    @Test
    void signedFileLinksExpireAndCannotBeForged() throws Exception {
        storage.put("materials/test/file.pdf", new byte[] {4, 2}, "application/pdf");
        String link = fileLinks.create("materials/test/file.pdf");
        assertThat(link).startsWith("http://node1.test:8081/internal/ai-node/files?key=");
        String path = link.substring("http://node1.test:8081".length());
        mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().bytes(new byte[] {4, 2}));
        mockMvc.perform(get(path.replace("file.pdf", "other.pdf"))).andExpect(status().isNotFound());
        mockMvc.perform(get("/internal/ai-node/files").param("key", "materials/test/file.pdf")
                .param("expires", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond())).param("sig", "00"))
                .andExpect(status().isNotFound());
    }

    private UUID submittedJob() {
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
                insert into ai_jobs (job_id, job_type, subject_type, subject_id, status, request, submitted_at)
                values (?, 'GRADE_THREAD', 'QUESTION_GRADE', ?, 'SUBMITTED', '{}'::jsonb, now())""", jobId, UUID.randomUUID());
        return jobId;
    }

    private String[] sign(String body) {
        long ts = Instant.now().getEpochSecond();
        return new String[]{String.valueOf(ts), AiNodeSignature.sign(SECRET, ts, body.getBytes(StandardCharsets.UTF_8))};
    }

    private ResultActions callback(UUID jobId, String body, String[] signature)
            throws Exception {
        return mockMvc.perform(post("/internal/ai-node/jobs/{id}/result", jobId).contentType(MediaType.APPLICATION_JSON)
                .header(AiNodeSignature.TIMESTAMP_HEADER, signature[0])
                .header(AiNodeSignature.SIGNATURE_HEADER, signature[1]).content(body));
    }
}
