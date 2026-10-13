package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.exam.ExamApi.AttemptInfo;
import swd392.group6.AIVES.exam.ExamApi.AttemptQuestionInfo;
import swd392.group6.AIVES.questionbank.QuestionBankApi;
import swd392.group6.AIVES.questionbank.QuestionBankApi.QuestionSnapshot;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** SQL implementation of {@link AttemptControlApi}; every method joins the caller's transaction. */
@Service
@RequiredArgsConstructor
@Transactional
class JdbcAttemptControl implements AttemptControlApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;
    private final ExamApi examApi;
    private final ExamTemplateRepository templates;
    private final ExamTemplateItemRepository items;
    private final ExamQueries queries;
    private final QuestionBankApi questionBank;

    @Override
    public Optional<AttemptInfo> lock(UUID attemptId) {
        List<UUID> found = jdbc.queryForList("select attempt_id from exam_attempts where attempt_id = ? for update",
                UUID.class, attemptId);
        return found.isEmpty() ? Optional.empty() : examApi.getAttempt(attemptId);
    }

    @Override
    public void touch(UUID attemptId, Instant at) {
        jdbc.update("update exam_attempts set last_seen_at = ? where attempt_id = ?", ts(at), attemptId);
    }

    @Override
    public void markDisconnected(UUID attemptId, Instant since) {
        jdbc.update("update exam_attempts set disconnected_since = ? where attempt_id = ? and disconnected_since is null",
                ts(since), attemptId);
    }

    @Override
    public void reconnect(UUID attemptId, int offlineSec, Instant now) {
        jdbc.update("""
                        update exam_attempts set disconnected_since = null, last_seen_at = ?, disconnect_count = disconnect_count + 1,
                               frozen_sec_total = frozen_sec_total + ?, deadline_at = deadline_at + make_interval(secs => ?)
                        where attempt_id = ?""",
                ts(now), offlineSec, offlineSec, attemptId);
    }

    @Override
    public void extendDeadline(UUID attemptId, int seconds) {
        if (seconds > 0) {
            jdbc.update("update exam_attempts set deadline_at = deadline_at + make_interval(secs => ?) where attempt_id = ?",
                    seconds, attemptId);
        }
    }

    @Override
    public void interrupt(UUID attemptId, Instant at) {
        jdbc.update("""
                        update exam_attempts set status = 'INTERRUPTED', disconnected_since = coalesce(disconnected_since, ?)
                        where attempt_id = ? and status = 'IN_PROGRESS'""", ts(at), attemptId);
    }

    @Override
    public void resume(UUID attemptId, Instant now) {
        jdbc.update("""
                        update exam_attempts set status = 'IN_PROGRESS', last_seen_at = ?,
                               frozen_sec_total = frozen_sec_total + greatest(0, extract(epoch from (? - coalesce(disconnected_since, ?)))::int),
                               deadline_at = deadline_at + greatest(interval '0', ? - coalesce(disconnected_since, ?)),
                               disconnected_since = null
                        where attempt_id = ? and status = 'INTERRUPTED'""",
                ts(now), ts(now), ts(now), ts(now), ts(now), attemptId);
    }

    @Override
    public void complete(UUID attemptId, String endReason, Instant at) {
        jdbc.update("""
                        update exam_attempts set status = 'COMPLETED', end_reason = ?, ended_at = ?, disconnected_since = null
                        where attempt_id = ? and status in ('IN_PROGRESS', 'INTERRUPTED')""", endReason, ts(at), attemptId);
    }

    @Override
    public void startQuestion(UUID attemptQuestionId, Instant at) {
        jdbc.update("""
                        update attempt_questions set status = 'IN_PROGRESS', started_at = coalesce(started_at, ?)
                        where attempt_question_id = ? and status = 'PENDING'""", ts(at), attemptQuestionId);
    }

    @Override
    public void finishQuestion(UUID attemptQuestionId, String status, Instant at) {
        jdbc.update("""
                        update attempt_questions set status = ?, ended_at = ?
                        where attempt_question_id = ? and status in ('PENDING', 'IN_PROGRESS')""",
                status, ts(at), attemptQuestionId);
    }

    @Override
    public void chargeTime(UUID attemptQuestionId, int seconds) {
        if (seconds > 0) {
            jdbc.update("update attempt_questions set time_used_sec = time_used_sec + ? where attempt_question_id = ?",
                    seconds, attemptQuestionId);
        }
    }

    @Override
    public Optional<AttemptQuestionInfo> replaceQuestion(UUID attemptQuestionId, String voidReason, Instant at) {
        record Old(UUID attemptId, UUID examId, UUID itemId, int orderNo, int budget, long seed) {
        }
        List<Old> rows = jdbc.query("""
                        select q.attempt_id, a.viva_exam_id, q.template_item_id, q.order_no, q.time_budget_sec,
                               coalesce(a.selection_seed, 0)
                        from attempt_questions q join exam_attempts a on a.attempt_id = q.attempt_id
                        where q.attempt_question_id = ? and q.status in ('PENDING', 'IN_PROGRESS')""",
                (rs, i) -> new Old(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getInt(4), rs.getInt(5), rs.getLong(6)), attemptQuestionId);
        if (rows.isEmpty() || rows.getFirst().itemId() == null) {
            return Optional.empty();
        }
        Old old = rows.getFirst();
        Optional<ExamTemplateItem> item = items.findById(old.itemId());
        Optional<ExamTemplate> template = item.flatMap(i -> templates.findById(i.getTemplateId()));
        if (template.isEmpty()) {
            return Optional.empty();
        }
        Set<UUID> excluded = new HashSet<>(jdbc.queryForList(
                "select question_id from attempt_questions where attempt_id = ?", UUID.class, old.attemptId()));
        Map<UUID, Integer> usage = new HashMap<>();
        jdbc.query("""
                select q.question_id, count(*) from attempt_questions q join exam_attempts a on a.attempt_id = q.attempt_id
                where a.viva_exam_id = ? and q.status <> 'VOIDED' group by q.question_id""",
                rs -> {
                    usage.put(rs.getObject(1, UUID.class), rs.getInt(2));
                }, old.examId());
        QuestionSelector.Row row = ExamQueries.rows(List.of(item.get())).getFirst();
        QuestionSelector.Draw draw = QuestionSelector.draw(queries.candidates(template.get()),
                List.of(new QuestionSelector.Row(row.topicId(), row.bloomLevel(), 1)), excluded, usage,
                old.seed() + excluded.size());
        if (draw.failed() || draw.picks().isEmpty()) {
            return Optional.empty();
        }
        QuestionSnapshot q = questionBank.snapshot(List.of(draw.picks().getFirst().questionId())).values().iterator().next();
        if (q.rubric() == null || q.rubric().criteria().isEmpty()) {
            return Optional.empty();
        }
        jdbc.update("""
                        update attempt_questions set status = 'VOIDED', void_reason = ?, ended_at = ?
                        where attempt_question_id = ?""", voidReason, ts(at), attemptQuestionId);
        UUID newId = UUID.randomUUID();
        jdbc.update("""
                        insert into attempt_questions (attempt_question_id, attempt_id, question_id, order_no, status, template_item_id,
                                                       topic_id, topic_name, bloom_level, language, content, reference_answer,
                                                       question_version, rubric_snapshot, time_budget_sec, replaces_attempt_question_id)
                        values (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)""",
                newId, old.attemptId(), q.questionId(), old.orderNo(), old.itemId(), q.topicId(), q.topicName(),
                q.bloomLevel() == null ? null : q.bloomLevel().name(), q.language().name(), q.content(),
                q.referenceAnswer(), q.version(), JSON.writeValueAsString(q.rubric()), old.budget(), attemptQuestionId);
        questionBank.lockForExam(List.of(q.questionId()), List.of(q.rubric().rubricId()));
        return examApi.getAttemptQuestions(old.attemptId()).stream()
                .filter(x -> x.attemptQuestionId().equals(newId)).findFirst();
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }
}
