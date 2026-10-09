package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.exam.ExamDtos.CreateTemplateRequest;
import swd392.group6.AIVES.exam.ExamDtos.QuestionPoolRequest;
import swd392.group6.AIVES.exam.ExamDtos.TemplateDetail;
import swd392.group6.AIVES.exam.ExamDtos.TemplateItemRequest;
import swd392.group6.AIVES.exam.ExamDtos.TemplateSummary;
import swd392.group6.AIVES.exam.ExamDtos.UpdateTemplateRequest;
import swd392.group6.AIVES.questionbank.BloomLevel;
import swd392.group6.AIVES.questionbank.QuestionBankApi;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.SettingsApi;
import swd392.group6.AIVES.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Đề thi CRUD: rows, question pool, lock and duplicate (D45, D46, 15 §5.3). */
@Service
@RequiredArgsConstructor
@Transactional
class ExamTemplateService {

    static final int MAX_QUESTIONS = 10;
    static final int MAX_DURATION_SEC = 3 * 3600;

    private final ExamTemplateRepository templates;
    private final ExamTemplateItemRepository items;
    private final ExamAccess access;
    private final ExamQueries queries;
    private final CourseAccessApi courseAccess;
    private final QuestionBankApi questionBank;
    private final SettingsApi settings;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    List<TemplateSummary> list(UUID courseId, boolean includeArchived, User user) {
        courseAccess.requireRead(courseId, user);
        return jdbc.queryForList("""
                        select exam_template_id from exam_templates where course_id = :c and (:all or not is_archived)
                        order by updated_at desc, title""",
                        new MapSqlParameterSource("c", courseId).addValue("all", includeArchived), UUID.class)
                .stream().map(id -> queries.summary(templates.findById(id).orElseThrow())).toList();
    }

    TemplateDetail create(UUID courseId, CreateTemplateRequest r, User user) {
        courseAccess.requireWrite(courseId, user);
        Instant now = clock.instant();
        ExamTemplate t = new ExamTemplate();
        t.setId(UUID.randomUUID());
        t.setCourseId(courseId);
        t.setTitle(r.title().trim());
        t.setDescription(VivaExamService.blankToNull(r.description()));
        t.setLanguage(r.language() != null ? r.language() : courseLanguage(courseId));
        t.setMaxFollowupsPerQuestion(orDefault(r.maxFollowupsPerQuestion(), 2));
        t.setMaxAnswerSec(orDefault(r.maxAnswerSec(), 120));
        t.setSilenceWarningSec(orDefault(r.silenceWarningSec(), 15));
        t.setShowQuestionText(r.showQuestionText() == null || r.showQuestionText());
        t.setPassScore(scale(r.passScore()));
        t.setRubricId(r.rubricId());
        t.setCreatedBy(user.getUserId());
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        validate(t);
        templates.saveAndFlush(t);
        if (r.items() != null && !r.items().isEmpty()) {
            saveItems(t, r.items());
        }
        return queries.templateDetail(t);
    }

    TemplateDetail get(UUID templateId, User user) {
        return queries.templateDetail(access.readTemplate(templateId, user));
    }

    TemplateDetail update(UUID templateId, UpdateTemplateRequest r, User user) {
        ExamTemplate t = editable(templateId, user);
        if (!r.version().equals(t.getVersion())) {
            throw versionConflict();
        }
        t.setTitle(r.title().trim());
        t.setDescription(VivaExamService.blankToNull(r.description()));
        t.setLanguage(r.language());
        t.setMaxFollowupsPerQuestion(r.maxFollowupsPerQuestion());
        t.setMaxAnswerSec(r.maxAnswerSec());
        t.setSilenceWarningSec(r.silenceWarningSec());
        t.setShowQuestionText(r.showQuestionText());
        t.setPassScore(scale(r.passScore()));
        t.setRubricId(r.rubricId());
        validate(t);
        return touch(t);
    }

    TemplateDetail replaceItems(UUID templateId, List<TemplateItemRequest> rows, User user) {
        ExamTemplate t = editable(templateId, user);
        saveItems(t, rows);
        return touch(t);
    }

    TemplateDetail replaceQuestionPool(UUID templateId, QuestionPoolRequest r, User user) {
        ExamTemplate t = editable(templateId, user);
        MapSqlParameterSource p = new MapSqlParameterSource("t", templateId);
        jdbc.update("delete from exam_template_questions where exam_template_id = :t", p);
        if (r.mode() == QuestionPoolMode.SELECTED) {
            Set<UUID> ids = new LinkedHashSet<>(r.questionIds() == null ? List.of() : r.questionIds());
            ids.remove(null);
            if (ids.isEmpty()) {
                throw ApiException.unprocessable("QUESTION_POOL_EMPTY", "Pick at least one question for a SELECTED pool");
            }
            Set<UUID> published = new HashSet<>();
            questionBank.findPublished(t.getCourseId(), null, null, ids).forEach(q -> published.add(q.questionId()));
            List<UUID> invalid = ids.stream().filter(id -> !published.contains(id)).toList();
            if (!invalid.isEmpty()) {
                throw ApiException.unprocessable("QUESTION_NOT_PUBLISHED", "Not PUBLISHED questions of this course: " + invalid);
            }
            jdbc.batchUpdate("insert into exam_template_questions (exam_template_id, question_id) values (:t, :q)",
                    ids.stream().map(id -> new MapSqlParameterSource("t", templateId).addValue("q", id))
                            .toArray(MapSqlParameterSource[]::new));
        }
        t.setQuestionPoolMode(r.mode());
        return touch(t);
    }

    /** A copy that can be edited again — the way to change a locked template (D49). */
    TemplateDetail duplicate(UUID templateId, String title, User user) {
        ExamTemplate source = access.readTemplate(templateId, user);
        courseAccess.requireWrite(source.getCourseId(), user);
        Instant now = clock.instant();
        ExamTemplate t = new ExamTemplate();
        t.setId(UUID.randomUUID());
        t.setCourseId(source.getCourseId());
        t.setTitle(title != null && !title.isBlank() ? title.trim() : copyTitle(source.getTitle()));
        t.setDescription(source.getDescription());
        t.setLanguage(source.getLanguage());
        t.setMaxFollowupsPerQuestion(source.getMaxFollowupsPerQuestion());
        t.setMaxAnswerSec(source.getMaxAnswerSec());
        t.setSilenceWarningSec(source.getSilenceWarningSec());
        t.setShowQuestionText(source.isShowQuestionText());
        t.setPassScore(source.getPassScore());
        t.setRubricId(source.getRubricId());
        t.setQuestionPoolMode(source.getQuestionPoolMode());
        t.setCreatedBy(user.getUserId());
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        templates.saveAndFlush(t);
        items.saveAllAndFlush(items.findByTemplateIdOrderBySortOrder(source.getId()).stream()
                .map(i -> new ExamTemplateItem(UUID.randomUUID(), t.getId(), i.getChapterId(), i.getBloomLevel(),
                        i.getQuestionCount(), i.getSecondsPerQuestion(), i.getRubricId(), i.getSortOrder()))
                .toList());
        jdbc.update("""
                insert into exam_template_questions (exam_template_id, question_id)
                select :n, question_id from exam_template_questions where exam_template_id = :t""",
                new MapSqlParameterSource("t", source.getId()).addValue("n", t.getId()));
        return queries.templateDetail(t);
    }

    /** Archived templates are hidden from the default list and cannot be used by new buổi thi. */
    TemplateDetail setArchived(UUID templateId, boolean archived, User user) {
        ExamTemplate t = access.writeTemplate(templateId, user);
        t.setArchived(archived);
        return touch(t);
    }

    void delete(UUID templateId, User user) {
        ExamTemplate t = access.writeTemplate(templateId, user);
        if (queries.usedByExamCount(templateId) > 0) {
            throw ApiException.conflict("EXAM_TEMPLATE_IN_USE", "A buổi thi uses this template; archive it instead");
        }
        jdbc.update("delete from exam_template_questions where exam_template_id = :t", new MapSqlParameterSource("t", templateId));
        items.deleteByTemplate(templateId);
        templates.delete(t);
    }

    /** Locks the template once a buổi thi using it is published (D49). */
    void lock(ExamTemplate t) {
        if (!t.isLocked()) {
            t.setLocked(true);
            t.setUpdatedAt(clock.instant());
            templates.saveAndFlush(t);
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private ExamTemplate editable(UUID templateId, User user) {
        ExamTemplate t = access.writeTemplate(templateId, user);
        if (t.isLocked()) {
            throw ApiException.conflict("EXAM_TEMPLATE_LOCKED",
                    "A published buổi thi uses this template; duplicate it to make changes");
        }
        return t;
    }

    private void saveItems(ExamTemplate t, List<TemplateItemRequest> rows) {
        if (rows.isEmpty()) {
            throw ApiException.unprocessable("TEMPLATE_ITEMS_REQUIRED", "A template needs at least one row");
        }
        List<ExamTemplateItem> result = new ArrayList<>();
        int total = 0;
        int duration = 0;
        int order = 0;
        for (TemplateItemRequest row : rows) {
            if (row.chapterId() != null && !questionBank.chapterBelongsToCourse(row.chapterId(), t.getCourseId())) {
                throw ApiException.unprocessable("CHAPTER_NOT_IN_COURSE", "Chapter " + row.chapterId() + " is not a chapter of this course");
            }
            if (row.rubricId() != null && !questionBank.rubricUsableInCourse(row.rubricId(), t.getCourseId())) {
                throw rubricInvalid(row.rubricId());
            }
            int seconds = row.secondsPerQuestion() != null ? row.secondsPerQuestion() : defaultSeconds(row.bloomLevel());
            if (seconds < 30 || seconds > 1800) {
                throw ApiException.unprocessable("INVALID_SECONDS_PER_QUESTION", "secondsPerQuestion must be 30–1800");
            }
            total += row.count();
            duration += row.count() * seconds;
            result.add(new ExamTemplateItem(UUID.randomUUID(), t.getId(), row.chapterId(), row.bloomLevel(), row.count(),
                    seconds, row.rubricId(), order++));
        }
        if (total > MAX_QUESTIONS) {
            throw ApiException.unprocessable("INVALID_QUESTION_COUNT", "A template has at most " + MAX_QUESTIONS
                    + " main questions (rows total " + total + ")");
        }
        if (duration > MAX_DURATION_SEC) {
            throw ApiException.unprocessable("INVALID_DURATION", "A template lasts at most 3 hours (rows total " + duration + " s)");
        }
        items.deleteByTemplate(t.getId());
        items.saveAllAndFlush(result);
    }

    /** {@code exam.seconds.<BLOOM>} (or {@code .ANY}) from the settings, D46. */
    int defaultSeconds(BloomLevel bloom) {
        String key = "exam.seconds." + (bloom == null ? "ANY" : bloom.name());
        int fallback = bloom == null ? 180 : switch (bloom) {
            case REMEMBER -> 120;
            case UNDERSTAND -> 180;
            case APPLY -> 240;
            case ANALYZE -> 300;
        };
        return settings.value(key).map(v -> {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }).orElse(fallback);
    }

    private void validate(ExamTemplate t) {
        if (t.getMaxFollowupsPerQuestion() < 0 || t.getMaxFollowupsPerQuestion() > 5) {
            throw ApiException.unprocessable("INVALID_FOLLOWUP_COUNT", "maxFollowupsPerQuestion must be 0–5");
        }
        if (t.getMaxAnswerSec() < 30 || t.getMaxAnswerSec() > 600) {
            throw ApiException.unprocessable("INVALID_ANSWER_TIME_LIMIT", "maxAnswerSec must be 30–600");
        }
        if (t.getSilenceWarningSec() < 5 || t.getSilenceWarningSec() > 120) {
            throw ApiException.unprocessable("INVALID_CONFIG", "silenceWarningSec must be 5–120");
        }
        if (t.getRubricId() != null && !questionBank.rubricUsableInCourse(t.getRubricId(), t.getCourseId())) {
            throw rubricInvalid(t.getRubricId());
        }
    }

    private static ApiException rubricInvalid(UUID rubricId) {
        return ApiException.unprocessable("RUBRIC_NOT_USABLE",
                "Rubric " + rubricId + " is not a rubric of this course with weights totalling 100");
    }

    private TemplateDetail touch(ExamTemplate t) {
        t.setUpdatedAt(clock.instant());
        return queries.templateDetail(templates.saveAndFlush(t));
    }

    private Language courseLanguage(UUID courseId) {
        List<String> lang = jdbc.queryForList("select default_language from courses where course_id = :c",
                new MapSqlParameterSource("c", courseId), String.class);
        return lang.isEmpty() ? Language.VI : Language.valueOf(lang.getFirst());
    }

    private static BigDecimal scale(BigDecimal score) {
        return score == null ? null : score.setScale(2, RoundingMode.HALF_UP);
    }

    private static int orDefault(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static String copyTitle(String title) {
        String t = title + " (bản sao)";
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    static ApiException versionConflict() {
        return ApiException.conflict("VERSION_CONFLICT", "It was changed by someone else; reload it");
    }
}
