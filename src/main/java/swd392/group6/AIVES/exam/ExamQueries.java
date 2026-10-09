package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.exam.ExamDtos.BlueprintItemView;
import swd392.group6.AIVES.exam.ExamDtos.ExamDetail;
import swd392.group6.AIVES.exam.ExamDtos.QuestionPoolView;
import swd392.group6.AIVES.questionbank.QuestionBankApi;
import swd392.group6.AIVES.questionbank.QuestionBankApi.PublishedQuestion;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared reads of the exam module: pool, selected questions, counts, the detail view. */
@Component
@RequiredArgsConstructor
class ExamQueries {

    private final NamedParameterJdbcTemplate jdbc;
    private final QuestionBankApi questionBank;
    private final BlueprintItemRepository blueprintItems;
    private final Clock clock;

    List<UUID> selectedQuestionIds(UUID examId) {
        return jdbc.queryForList("select question_id from viva_exam_questions where viva_exam_id = :e order by question_id",
                params(examId), UUID.class);
    }

    /**
     * Pool P of 15 §3: SELECTED → the picked questions still PUBLISHED; COURSE_BANK → every PUBLISHED question of
     * the course, narrowed by the legacy topic / Bloom filters only when there is no blueprint.
     */
    List<PublishedQuestion> pool(VivaExam exam, boolean hasBlueprint) {
        if (exam.getQuestionPoolMode() == QuestionPoolMode.SELECTED) {
            List<UUID> selected = selectedQuestionIds(exam.getId());
            // An empty restriction means "no restriction" to the question bank: never pass it.
            return selected.isEmpty() ? List.of() : questionBank.findPublished(exam.getCourseId(), null, null, selected);
        }
        if (hasBlueprint) {
            return questionBank.findPublished(exam.getCourseId(), null, null, null);
        }
        return questionBank.findPublished(exam.getCourseId(), exam.getTopicIds(), exam.getBloomLevels(), null);
    }

    long studentCount(UUID examId) {
        Long n = jdbc.queryForObject("select count(*) from viva_exam_students where viva_exam_id = :e", params(examId), Long.class);
        return n == null ? 0 : n;
    }

    Map<SessionStage, Long> stageCounts(VivaExam exam) {
        Map<SessionStage, Long> counts = new EnumMap<>(SessionStage.class);
        for (SessionStage stage : SessionStage.values()) {
            counts.put(stage, 0L);
        }
        Instant now = clock.instant();
        jdbc.query("select status, cancel_reason from exam_sessions where viva_exam_id = :e", params(exam.getId()),
                rs -> {
                    SessionStage stage = SessionStage.of(rs.getString(1), rs.getString(2), exam.getStatus(),
                            exam.getWindowStart(), exam.getWindowEnd(), now);
                    counts.merge(stage, 1L, Long::sum);
                });
        return counts;
    }

    /** True when any lượt thi of the exam has been started (BR-E6). */
    boolean anySessionStarted(UUID examId) {
        Long n = jdbc.queryForObject("""
                select count(*) from exam_sessions where viva_exam_id = :e
                  and (status in ('IN_PROGRESS', 'INTERRUPTED', 'COMPLETED') or started_at is not null)""",
                params(examId), Long.class);
        return n != null && n > 0;
    }

    boolean anySessionInProgress(UUID examId) {
        Long n = jdbc.queryForObject("""
                select count(*) from exam_sessions where viva_exam_id = :e and status in ('IN_PROGRESS', 'INTERRUPTED')""",
                params(examId), Long.class);
        return n != null && n > 0;
    }

    ExamDetail detail(VivaExam e) {
        List<BlueprintItem> items = blueprintItems.findByVivaExamIdOrderBySortOrder(e.getId());
        List<BlueprintItemView> blueprint = items.stream()
                .map(b -> new BlueprintItemView(b.getId(), b.getTopicId(), b.getBloomLevel(), b.getQuestionCount(), b.getSortOrder()))
                .toList();
        List<UUID> selected = e.getQuestionPoolMode() == QuestionPoolMode.SELECTED ? selectedQuestionIds(e.getId()) : List.of();
        QuestionPoolView pool = new QuestionPoolView(e.getQuestionPoolMode(), selected, pool(e, !items.isEmpty()).size());
        return new ExamDetail(e.getId(), e.getCourseId(), e.getTitle(), e.getDescription(), e.getInstructions(),
                e.getLocation(), e.getStatus(), e.getCreatedBy(), e.getExaminerId(), e.getWindowStart(), e.getWindowEnd(),
                e.getLanguage(), e.getMainQuestionCount(), e.getMaxFollowupsPerQuestion(), e.getTimeLimitPerStudentSec(),
                e.getAnswerTimeLimitSec(), e.getSilenceWarningSec(), e.getReconnectGraceSec(), e.getTopicIds(),
                e.getBloomLevels(), e.getSelectionStrategy(), e.isShowQuestionText(), blueprint, pool,
                studentCount(e.getId()), stageCounts(e), e.isResultsReleased(), e.getResultsReleasedAt(),
                e.getRetakeOfVivaExamId(), e.getCancelReason(), e.getVersion() == null ? 0 : e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    static MapSqlParameterSource params(UUID examId) {
        return new MapSqlParameterSource("e", examId);
    }

    static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
