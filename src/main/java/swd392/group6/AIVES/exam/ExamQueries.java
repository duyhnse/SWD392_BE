package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.exam.ExamDtos.ExamDetail;
import swd392.group6.AIVES.exam.ExamDtos.TemplateDetail;
import swd392.group6.AIVES.exam.ExamDtos.TemplateItemView;
import swd392.group6.AIVES.exam.ExamDtos.TemplateSummary;
import swd392.group6.AIVES.questionbank.QuestionBankApi;
import swd392.group6.AIVES.questionbank.QuestionBankApi.TopicInfo;
import swd392.group6.AIVES.questionbank.QuestionBankApi.PublishedQuestion;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared reads of the exam module: template rows and pool, counts, the detail views. */
@Component
@RequiredArgsConstructor
class ExamQueries {

    private final NamedParameterJdbcTemplate jdbc;
    private final QuestionBankApi questionBank;
    private final ExamTemplateItemRepository items;
    private final ExamTemplateRepository templates;
    private final Clock clock;

    List<UUID> selectedQuestionIds(UUID templateId) {
        return jdbc.queryForList("select question_id from exam_template_questions where exam_template_id = :t order by question_id",
                new MapSqlParameterSource("t", templateId), UUID.class);
    }

    /** Pool P of 15 §3: SELECTED → the picked questions still PUBLISHED; COURSE_BANK → every PUBLISHED question. */
    List<PublishedQuestion> pool(ExamTemplate t) {
        if (t.getQuestionPoolMode() == QuestionPoolMode.SELECTED) {
            List<UUID> selected = selectedQuestionIds(t.getId());
            // An empty restriction means "no restriction" to the question bank: never pass it.
            return selected.isEmpty() ? List.of() : questionBank.findPublished(t.getCourseId(), null, null, selected);
        }
        return questionBank.findPublished(t.getCourseId(), null, null, null);
    }

    List<QuestionSelector.Candidate> candidates(ExamTemplate t) {
        return pool(t).stream()
                .map(q -> new QuestionSelector.Candidate(q.questionId(), q.topicId(), q.bloomLevel())).toList();
    }

    static List<QuestionSelector.Row> rows(List<ExamTemplateItem> items) {
        return items.stream().map(i -> new QuestionSelector.Row(i.getTopicId(), i.getBloomLevel(), i.getQuestionCount()))
                .toList();
    }

    static int questionCount(List<ExamTemplateItem> items) {
        return items.stream().mapToInt(ExamTemplateItem::getQuestionCount).sum();
    }

    /** Σ count × seconds — the longest an attempt can take without frozen time (D46). */
    static int durationSec(List<ExamTemplateItem> items) {
        return items.stream().mapToInt(i -> i.getQuestionCount() * i.getSecondsPerQuestion()).sum();
    }

    long usedByExamCount(UUID templateId) {
        Long n = jdbc.queryForObject("select count(*) from viva_exams where exam_template_id = :t",
                new MapSqlParameterSource("t", templateId), Long.class);
        return n == null ? 0 : n;
    }

    TemplateSummary summary(ExamTemplate t) {
        List<ExamTemplateItem> rows = items.findByTemplateIdOrderBySortOrder(t.getId());
        return new TemplateSummary(t.getId(), t.getCourseId(), t.getTitle(), questionCount(rows), durationSec(rows),
                t.getPassScore(), t.isLocked(), t.isArchived(), usedByExamCount(t.getId()), version(t.getVersion()),
                t.getUpdatedAt());
    }

    TemplateDetail templateDetail(ExamTemplate t) {
        List<ExamTemplateItem> rows = items.findByTemplateIdOrderBySortOrder(t.getId());
        Map<UUID, TopicInfo> topics = questionBank.topics(t.getCourseId());
        List<QuestionSelector.Candidate> pool = candidates(t);
        List<TemplateItemView> views = rows.stream().map(i -> {
            QuestionSelector.Row row = new QuestionSelector.Row(i.getTopicId(), i.getBloomLevel(), i.getQuestionCount());
            TopicInfo topic = i.getTopicId() == null ? null : topics.get(i.getTopicId());
            return new TemplateItemView(i.getId(), i.getTopicId(), topic == null ? null : topic.name(),
                    i.getBloomLevel(), i.getQuestionCount(),
                    i.getSecondsPerQuestion(), i.getRubricId(), i.getSortOrder(), (int) pool.stream().filter(row::matches).count());
        }).toList();
        boolean sufficient = !rows.isEmpty() && QuestionSelector.shortages(pool, rows(rows)).isEmpty();
        List<UUID> selected = t.getQuestionPoolMode() == QuestionPoolMode.SELECTED ? selectedQuestionIds(t.getId()) : List.of();
        return new TemplateDetail(t.getId(), t.getCourseId(), t.getTitle(), t.getDescription(), t.getLanguage(),
                t.getMaxFollowupsPerQuestion(), t.getMaxAnswerSec(), t.getSilenceWarningSec(), t.isShowQuestionText(),
                t.getPassScore(), t.getRubricId(), t.getQuestionPoolMode(), selected, views, questionCount(rows),
                durationSec(rows), sufficient, t.isLocked(), t.isArchived(), usedByExamCount(t.getId()), t.getCreatedBy(),
                version(t.getVersion()), t.getCreatedAt(), t.getUpdatedAt());
    }

    long studentCount(UUID examId) {
        Long n = jdbc.queryForObject("select count(*) from viva_exam_students where viva_exam_id = :e", params(examId), Long.class);
        return n == null ? 0 : n;
    }

    /** Stage of every roster student (D31): attempt status when checked in, otherwise from the window. */
    Map<ExamStage, Long> stageCounts(VivaExam exam) {
        Map<ExamStage, Long> counts = new EnumMap<>(ExamStage.class);
        for (ExamStage stage : ExamStage.values()) {
            counts.put(stage, 0L);
        }
        Instant now = clock.instant();
        jdbc.query("""
                        select a.status from viva_exam_students v
                        left join exam_attempts a on a.viva_exam_id = v.viva_exam_id and a.student_id = v.student_id
                        where v.viva_exam_id = :e""", params(exam.getId()),
                rs -> {
                    ExamStage stage = ExamStage.of(rs.getString(1), exam.getStatus(), exam.getCheckinOpensAt(),
                            exam.getCheckinClosesAt(), now);
                    counts.merge(stage, 1L, Long::sum);
                });
        return counts;
    }

    boolean anyAttempt(UUID examId) {
        Long n = jdbc.queryForObject("select count(*) from exam_attempts where viva_exam_id = :e", params(examId), Long.class);
        return n != null && n > 0;
    }

    boolean anyAttemptRunning(UUID examId) {
        Long n = jdbc.queryForObject("""
                select count(*) from exam_attempts where viva_exam_id = :e and status in ('IN_PROGRESS', 'INTERRUPTED')""",
                params(examId), Long.class);
        return n != null && n > 0;
    }

    ExamDetail detail(VivaExam e) {
        ExamTemplate template = templates.findById(e.getTemplateId()).orElseThrow();
        TemplateSummary summary = summary(template);
        int duration = summary.totalDurationSec();
        Instant lastEnd = e.getCheckinClosesAt().plusSeconds((long) duration + e.getMaxFrozenSec());
        return new ExamDetail(e.getId(), e.getCourseId(), e.getTitle(), e.getDescription(), e.getInstructions(),
                e.getLocation(), e.getStatus(), e.getCreatedBy(), e.getExaminerId(), summary, e.getCheckinOpensAt(),
                e.getCheckinClosesAt(), duration, lastEnd, e.getReconnectGraceSec(), e.getMaxDisconnects(),
                e.getMaxFrozenSec(), e.getReplaceMainAfterSec(), studentCount(e.getId()), stageCounts(e),
                e.isResultsReleased(), e.getResultsReleasedAt(), e.getRetakeOfVivaExamId(), e.getCancelReason(),
                version(e.getVersion()), e.getCreatedAt(), e.getUpdatedAt());
    }

    static int version(Integer v) {
        return v == null ? 0 : v;
    }

    static MapSqlParameterSource params(UUID examId) {
        return new MapSqlParameterSource("e", examId);
    }

    static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
