package swd392.group6.AIVES.questionbank.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.ai.AiJobFinishedEvent;
import swd392.group6.AIVES.ai.AiJobService;
import swd392.group6.AIVES.ai.AiJobType;
import swd392.group6.AIVES.ai.EmbeddingPort;
import swd392.group6.AIVES.user.NotificationApi;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Results of the question bank's AI jobs (contract 16 §4): {@code INDEX_MATERIAL} → chunks + INDEXED / FAILED
 * (04 §9.1), {@code GENERATE_QUESTIONS} → drafts ({@link QuestionGenerationService#onJobFinished}). Runs after the
 * job row is stored, in its own transaction; a result for something deleted meanwhile is ignored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiResultListener {

    private final AiJobService aiJobs;
    private final JdbcTemplate jdbc;
    private final EmbeddingPort embeddings;
    private final QuestionGenerationService generations;
    private final NotificationApi notifications;
    private final Clock clock;

    /** REQUIRES_NEW: the mock finishes jobs from the caller's afterCommit, where the old transaction is still bound. */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFinished(AiJobFinishedEvent event) {
        if (event.type() == AiJobType.INDEX_MATERIAL && MaterialService.JOB_SUBJECT.equals(event.subjectType())) {
            aiJobs.find(event.jobId()).ifPresent(job -> indexed(event.subjectId(), job.status(), job.result(),
                    job.errorCode(), job.errorMessage()));
        } else if (event.type() == AiJobType.GENERATE_QUESTIONS
                && QuestionGenerationService.JOB_SUBJECT.equals(event.subjectType())) {
            aiJobs.find(event.jobId()).ifPresent(job -> generations.onJobFinished(event.subjectId(), job.status(),
                    job.request(), job.result(), job.errorCode(), job.errorMessage()));
        }
    }

    private void indexed(UUID materialId, String status, JsonNode result, String errorCode, String errorMessage) {
        var row = jdbc.queryForList("select status, uploaded_by, course_id, file_name from course_materials where material_id = ? for update",
                materialId).stream().findFirst().orElse(null);
        if (row == null || !"PROCESSING".equals(row.get("status"))) {
            return;
        }
        String problem = "SUCCEEDED".equals(status) ? chunkProblem(result) : (errorCode != null ? errorCode : "FAILED")
                + (errorMessage != null ? ": " + errorMessage : "");
        Timestamp now = Timestamp.from(clock.instant());
        if (problem == null) {
            jdbc.update("delete from material_chunks where material_id = ?", materialId);
            for (JsonNode c : result.path("chunks")) {
                StringBuilder vector = new StringBuilder("[");
                for (JsonNode x : c.path("embedding")) {
                    vector.append(vector.length() > 1 ? "," : "").append(x.asDouble());
                }
                jdbc.update("""
                                insert into material_chunks (chunk_id, material_id, chunk_index, content, location_label,
                                                             page_from, page_to, token_count, embedding)
                                values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as vector))""",
                        UUID.randomUUID(), materialId, c.path("chunk_index").asInt(), c.path("content").asString(),
                        truncate(c.path("location_label").asString("—"), 100), intOrNull(c.path("page_from")),
                        intOrNull(c.path("page_to")), intOrNull(c.path("token_count")), vector.append(']').toString());
            }
            jdbc.update("""
                            update course_materials set status = 'INDEXED', page_count = ?, error_message = null, indexed_at = ?
                            where material_id = ?""", intOrNull(result.path("page_count")), now, materialId);
        } else {
            jdbc.update("update course_materials set status = 'FAILED', error_message = ? where material_id = ?",
                    truncate(problem, 1000), materialId);
        }
        boolean ok = problem == null;
        notifications.notify((UUID) row.get("uploaded_by"), "MATERIAL_INDEXED",
                (ok ? "Đã xử lý xong tài liệu " : "Không xử lý được tài liệu ") + row.get("file_name"),
                ok ? "Có thể dùng tài liệu này để AI tạo câu hỏi." : problem,
                Map.of("materialId", materialId.toString(), "courseId", row.get("course_id").toString(),
                        "status", ok ? "INDEXED" : "FAILED"));
    }

    /** null = usable: at least one chunk, each with text and an embedding of the configured dimension. */
    private String chunkProblem(JsonNode result) {
        JsonNode chunks = result == null ? null : result.path("chunks");
        if (chunks == null || !chunks.isArray() || chunks.isEmpty()) {
            return "NO_TEXT_LAYER: no text was found in the file";
        }
        for (JsonNode c : chunks) {
            if (c.path("content").asString("").isBlank() || c.path("embedding").size() != embeddings.dimension()) {
                return "INVALID_RESULT: a chunk has no text or a wrong embedding dimension";
            }
        }
        return null;
    }

    private static Integer intOrNull(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.asInt();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
