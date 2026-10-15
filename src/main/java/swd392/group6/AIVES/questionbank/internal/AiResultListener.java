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
import swd392.group6.AIVES.user.NotificationApi;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Results of the question bank's AI jobs (contract 16 §4): {@code INDEX_MATERIAL} → INDEXED / FAILED (04 §9.1), {@code GENERATE_QUESTIONS} → drafts ({@link QuestionGenerationService#onJobFinished}). Runs after the
 * job row is stored, in its own transaction; a result for something deleted meanwhile is ignored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiResultListener {

    private final AiJobService aiJobs;
    private final JdbcTemplate jdbc;
    private final QuestionGenerationService generations;
    private final NotificationApi notifications;
    private final Clock clock;

    /** REQUIRES_NEW: a job can finish inside the caller's afterCommit, where the old transaction is still bound. */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFinished(AiJobFinishedEvent event) {
        if (event.type() == AiJobType.INDEX_MATERIAL && MaterialService.JOB_SUBJECT.equals(event.subjectType())) {
            aiJobs.find(event.jobId()).ifPresent(job -> indexed(event.subjectId(), job.status(), job.result(),
                    job.errorCode(), job.errorMessage()));
        } else if (event.type() == AiJobType.GENERATE_QUESTIONS
                && QuestionGenerationService.JOB_SUBJECT.equals(event.subjectType())) {
            aiJobs.find(event.jobId()).ifPresent(job -> generations.onJobFinished(event.subjectId(), job.status(),
                    job.result(), job.errorCode(), job.errorMessage()));
        }
    }

    private void indexed(UUID materialId, String status, JsonNode result, String errorCode, String errorMessage) {
        var row = jdbc.queryForList("select status, uploaded_by, course_id, file_name from course_materials where material_id = ? for update",
                materialId).stream().findFirst().orElse(null);
        if (row == null || !"PROCESSING".equals(row.get("status"))) {
            return;
        }
        boolean ok = "SUCCEEDED".equals(status) && result != null && result.path("chunk_count").asInt(0) > 0;
        String problem = ok ? null : "SUCCEEDED".equals(status) ? "NO_TEXT_LAYER: no text was found in the file"
                : (errorCode != null ? errorCode : "FAILED") + (errorMessage != null ? ": " + errorMessage : "");
        if (ok) {
            jdbc.update("""
                            update course_materials set status = 'INDEXED', page_count = ?, error_message = null, indexed_at = ?
                            where material_id = ?""", intOrNull(result.path("page_count")),
                    Timestamp.from(clock.instant()), materialId);
        } else {
            jdbc.update("update course_materials set status = 'FAILED', error_message = ? where material_id = ?",
                    truncate(problem, 1000), materialId);
        }
        notifications.notify((UUID) row.get("uploaded_by"), "MATERIAL_INDEXED",
                (ok ? "Đã xử lý xong tài liệu " : "Không xử lý được tài liệu ") + row.get("file_name"),
                ok ? "Có thể dùng tài liệu này để AI tạo câu hỏi." : problem,
                Map.of("materialId", materialId.toString(), "courseId", row.get("course_id").toString(),
                        "status", ok ? "INDEXED" : "FAILED"));
    }

    private static Integer intOrNull(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.asInt();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
