package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.exam.ExamDtos.BlueprintItemRequest;
import swd392.group6.AIVES.exam.ExamDtos.BlueprintRequest;
import swd392.group6.AIVES.exam.ExamDtos.CreateExamRequest;
import swd392.group6.AIVES.exam.ExamDtos.ExamDetail;
import swd392.group6.AIVES.exam.ExamDtos.ExamSummary;
import swd392.group6.AIVES.exam.ExamDtos.QuestionPoolRequest;
import swd392.group6.AIVES.exam.ExamDtos.UpdateExamRequest;
import swd392.group6.AIVES.questionbank.QuestionBankApi;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.User;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Buổi thi CRUD, blueprint and question pool (15 §5.3, 07 §3). */
@Service
@RequiredArgsConstructor
@Transactional
class VivaExamService {

    private final VivaExamRepository exams;
    private final BlueprintItemRepository blueprintItems;
    private final ExamAccess access;
    private final ExamQueries queries;
    private final ExamStatusRefresher refresher;
    private final CourseAccessApi courseAccess;
    private final QuestionBankApi questionBank;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    PageResponse<ExamSummary> list(UUID courseId, User user, List<ExamStatus> statuses, String when, int page, int size) {
        courseAccess.requireRead(courseId, user);
        refresher.refreshDue();
        int p = Math.max(page, 0);
        int s = Math.clamp(size, 1, 100);
        StringBuilder where = new StringBuilder(" where e.course_id = :c");
        MapSqlParameterSource params = new MapSqlParameterSource("c", courseId)
                .addValue("now", java.sql.Timestamp.from(clock.instant()));
        if (statuses != null && !statuses.isEmpty()) {
            where.append(" and e.status in (:st)");
            params.addValue("st", statuses.stream().map(Enum::name).toList());
        }
        if (when != null && !when.isBlank()) {
            where.append(switch (when.trim().toLowerCase(Locale.ROOT)) {
                case "upcoming" -> " and e.window_start > :now";
                case "ongoing" -> " and e.window_start <= :now and e.window_end >= :now";
                case "past" -> " and e.window_end < :now";
                default -> throw ApiException.unprocessable("INVALID_FILTER", "when must be upcoming, ongoing or past");
            });
        }
        Long total = jdbc.queryForObject("select count(*) from viva_exams e" + where, params, Long.class);
        params.addValue("limit", s).addValue("offset", (long) p * s);
        List<ExamSummary> items = jdbc.query("""
                select e.viva_exam_id, e.course_id, e.title, e.status, e.window_start, e.window_end, e.main_question_count,
                       e.time_limit_per_student_sec, e.results_released, e.retake_of_viva_exam_id, e.version,
                       (select count(*) from viva_exam_students v where v.viva_exam_id = e.viva_exam_id) as students,
                       (select count(*) from exam_sessions x where x.viva_exam_id = e.viva_exam_id
                          and x.status = 'COMPLETED') as completed,
                       (select count(*) from exam_sessions x join grade_evaluations g on g.session_id = x.session_id
                          where x.viva_exam_id = e.viva_exam_id and g.status = 'CONFIRMED') as confirmed
                from viva_exams e""" + where + " order by e.window_start desc, e.title limit :limit offset :offset",
                params, (rs, i) -> new ExamSummary(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), ExamStatus.valueOf(rs.getString(4)), ExamQueries.instant(rs.getTimestamp(5)),
                        ExamQueries.instant(rs.getTimestamp(6)), rs.getInt(7), rs.getInt(8), rs.getLong(12),
                        rs.getLong(13), rs.getLong(14), rs.getBoolean(9), rs.getObject(10, UUID.class), rs.getInt(11)));
        return new PageResponse<>(items, p, s, total == null ? 0 : total);
    }

    ExamDetail create(UUID courseId, CreateExamRequest r, User user) {
        courseAccess.requireWrite(courseId, user);
        Instant now = clock.instant();
        VivaExam e = new VivaExam();
        e.setId(UUID.randomUUID());
        e.setCourseId(courseId);
        e.setTitle(r.title().trim());
        e.setDescription(blankToNull(r.description()));
        e.setInstructions(blankToNull(r.instructions()));
        e.setLocation(blankToNull(r.location()));
        e.setCreatedBy(user.getUserId());
        e.setExaminerId(r.examinerId() != null ? r.examinerId() : user.getUserId());
        e.setWindowStart(r.windowStart());
        e.setWindowEnd(r.windowEnd());
        e.setLanguage(r.language() != null ? r.language() : courseLanguage(courseId));
        e.setMainQuestionCount(orDefault(r.mainQuestionCount(), 3));
        e.setMaxFollowupsPerQuestion(orDefault(r.maxFollowupsPerQuestion(), 2));
        e.setTimeLimitPerStudentSec(orDefault(r.timeLimitPerStudentSec(), 900));
        e.setAnswerTimeLimitSec(orDefault(r.answerTimeLimitSec(), 120));
        e.setSilenceWarningSec(orDefault(r.silenceWarningSec(), 15));
        e.setReconnectGraceSec(orDefault(r.reconnectGraceSec(), 60));
        e.setTopicIds(emptyToNull(r.topicIds()));
        e.setBloomLevels(emptyToNull(r.bloomLevels()));
        e.setShowQuestionText(r.showQuestionText() == null || r.showQuestionText());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        validateConfig(e);
        return queries.detail(exams.saveAndFlush(e));
    }

    ExamDetail get(UUID examId, User user) {
        refresher.refreshDue();
        return queries.detail(access.read(examId, user));
    }

    ExamDetail update(UUID examId, UpdateExamRequest r, User user) {
        refresher.refreshDue();
        VivaExam e = access.write(examId, user);
        if (!r.version().equals(e.getVersion())) {
            throw ApiException.conflict("VERSION_CONFLICT", "The exam was changed by someone else; reload it");
        }
        switch (e.getStatus()) {
            case DRAFT -> applyConfig(e, r);
            case READY, OPEN -> {
                if (r.touchesConfig()) {
                    throw frozen();
                }
            }
            default -> throw ApiException.conflict("EXAM_NOT_EDITABLE", "A closed or cancelled exam cannot be edited");
        }
        if (r.title() != null) {
            if (r.title().isBlank()) {
                throw ApiException.unprocessable("TITLE_REQUIRED", "Title must not be blank");
            }
            e.setTitle(r.title().trim());
        }
        if (r.description() != null) {
            e.setDescription(blankToNull(r.description()));
        }
        if (r.instructions() != null) {
            e.setInstructions(blankToNull(r.instructions()));
        }
        if (r.location() != null) {
            e.setLocation(blankToNull(r.location()));
        }
        e.setUpdatedAt(clock.instant());
        return queries.detail(exams.saveAndFlush(e));
    }

    void delete(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam e = access.write(examId, user);
        if (e.getStatus() != ExamStatus.DRAFT) {
            throw ApiException.conflict("EXAM_NOT_DRAFT", "Only a DRAFT exam can be deleted; cancel it instead");
        }
        MapSqlParameterSource p = ExamQueries.params(examId);
        Long retakes = jdbc.queryForObject("select count(*) from viva_exams where retake_of_viva_exam_id = :e", p, Long.class);
        if (retakes != null && retakes > 0) {
            throw ApiException.conflict("EXAM_IN_USE", "A retake was created from this exam");
        }
        jdbc.update("delete from session_questions where session_id in (select session_id from exam_sessions where viva_exam_id = :e)", p);
        jdbc.update("delete from exam_sessions where viva_exam_id = :e", p);
        jdbc.update("delete from viva_exam_students where viva_exam_id = :e", p);
        jdbc.update("delete from viva_exam_questions where viva_exam_id = :e", p);
        blueprintItems.deleteByExam(examId);
        exams.delete(e);
    }

    ExamDetail replaceBlueprint(UUID examId, BlueprintRequest r, User user) {
        refresher.refreshDue();
        VivaExam e = access.write(examId, user);
        requireDraft(e);
        List<BlueprintItem> items = new ArrayList<>();
        int total = 0;
        int order = 0;
        for (BlueprintItemRequest item : r.items()) {
            if (item.topicId() != null && !questionBank.topicBelongsToCourse(item.topicId(), e.getCourseId())) {
                throw ApiException.unprocessable("TOPIC_NOT_IN_COURSE", "Topic " + item.topicId() + " is not a topic of this course");
            }
            total += item.count();
            items.add(new BlueprintItem(UUID.randomUUID(), examId, item.topicId(), item.bloomLevel(), item.count(), order++));
        }
        if (!items.isEmpty() && total != e.getMainQuestionCount()) {
            throw blueprintMismatch(total, e.getMainQuestionCount());
        }
        blueprintItems.deleteByExam(examId);
        blueprintItems.saveAllAndFlush(items);
        e.setUpdatedAt(clock.instant());
        return queries.detail(exams.saveAndFlush(e));
    }

    ExamDetail replaceQuestionPool(UUID examId, QuestionPoolRequest r, User user) {
        refresher.refreshDue();
        VivaExam e = access.write(examId, user);
        requireDraft(e);
        MapSqlParameterSource p = ExamQueries.params(examId);
        jdbc.update("delete from viva_exam_questions where viva_exam_id = :e", p);
        if (r.mode() == QuestionPoolMode.SELECTED) {
            Set<UUID> ids = new LinkedHashSet<>(r.questionIds() == null ? List.of() : r.questionIds());
            ids.remove(null);
            if (ids.isEmpty()) {
                throw ApiException.unprocessable("QUESTION_POOL_EMPTY", "Pick at least one question for a SELECTED pool");
            }
            Set<UUID> published = new HashSet<>();
            questionBank.findPublished(e.getCourseId(), null, null, ids).forEach(q -> published.add(q.questionId()));
            List<UUID> invalid = ids.stream().filter(id -> !published.contains(id)).toList();
            if (!invalid.isEmpty()) {
                throw ApiException.unprocessable("QUESTION_NOT_PUBLISHED",
                        "Not PUBLISHED questions of this course: " + invalid);
            }
            jdbc.batchUpdate("insert into viva_exam_questions (viva_exam_id, question_id) values (:e, :q)",
                    ids.stream().map(id -> new MapSqlParameterSource("e", examId).addValue("q", id))
                            .toArray(MapSqlParameterSource[]::new));
        }
        e.setQuestionPoolMode(r.mode());
        e.setUpdatedAt(clock.instant());
        return queries.detail(exams.saveAndFlush(e));
    }

    // ---- helpers shared with the other services -------------------------------------------------------------

    /** 07 §3 config validation, 422 with a specific code per field. */
    void validateConfig(VivaExam e) {
        if (!e.getWindowEnd().isAfter(e.getWindowStart())) {
            throw ApiException.unprocessable("INVALID_WINDOW", "windowEnd must be after windowStart");
        }
        int n = e.getMainQuestionCount();
        if (n < 1 || n > 10) {
            throw ApiException.unprocessable("INVALID_QUESTION_COUNT", "mainQuestionCount must be 1–10");
        }
        if (e.getMaxFollowupsPerQuestion() < 0 || e.getMaxFollowupsPerQuestion() > 5) {
            throw ApiException.unprocessable("INVALID_FOLLOWUP_COUNT", "maxFollowupsPerQuestion must be 0–5");
        }
        int t = e.getTimeLimitPerStudentSec();
        if (t < 120 || t > 3600 || t < n * 60) {
            throw ApiException.unprocessable("INVALID_TIME_LIMIT",
                    "timeLimitPerStudentSec must be 120–3600 and at least 60 s per main question (" + n * 60 + ")");
        }
        if (e.getAnswerTimeLimitSec() < 30 || e.getAnswerTimeLimitSec() > 600) {
            throw ApiException.unprocessable("INVALID_ANSWER_TIME_LIMIT", "answerTimeLimitSec must be 30–600");
        }
        if (e.getSilenceWarningSec() < 1 || e.getReconnectGraceSec() < 0) {
            throw ApiException.unprocessable("INVALID_CONFIG", "silenceWarningSec must be ≥ 1 and reconnectGraceSec ≥ 0");
        }
        if (!courseAccess.isLecturerOf(e.getCourseId(), e.getExaminerId())) {
            throw ApiException.unprocessable("INVALID_EXAMINER", "The examiner must be a lecturer assigned to the course");
        }
        if (e.getTopicIds() != null) {
            for (UUID topicId : e.getTopicIds()) {
                if (topicId == null || !questionBank.topicBelongsToCourse(topicId, e.getCourseId())) {
                    throw ApiException.unprocessable("TOPIC_NOT_IN_COURSE", "Topic " + topicId + " is not a topic of this course");
                }
            }
        }
    }

    static void requireDraft(VivaExam e) {
        if (e.getStatus() != ExamStatus.DRAFT) {
            throw frozen();
        }
    }

    static ApiException frozen() {
        return ApiException.conflict("EXAM_CONFIG_FROZEN",
                "Question sets are generated: only title, description, instructions and location can change");
    }

    static ApiException blueprintMismatch(int total, int expected) {
        return ApiException.unprocessable("BLUEPRINT_COUNT_MISMATCH",
                "Blueprint rows total " + total + " questions but mainQuestionCount is " + expected);
    }

    private void applyConfig(VivaExam e, UpdateExamRequest r) {
        if (r.examinerId() != null) {
            e.setExaminerId(r.examinerId());
        }
        if (r.windowStart() != null) {
            e.setWindowStart(r.windowStart());
        }
        if (r.windowEnd() != null) {
            e.setWindowEnd(r.windowEnd());
        }
        if (r.language() != null) {
            e.setLanguage(r.language());
        }
        if (r.mainQuestionCount() != null) {
            e.setMainQuestionCount(r.mainQuestionCount());
        }
        if (r.maxFollowupsPerQuestion() != null) {
            e.setMaxFollowupsPerQuestion(r.maxFollowupsPerQuestion());
        }
        if (r.timeLimitPerStudentSec() != null) {
            e.setTimeLimitPerStudentSec(r.timeLimitPerStudentSec());
        }
        if (r.answerTimeLimitSec() != null) {
            e.setAnswerTimeLimitSec(r.answerTimeLimitSec());
        }
        if (r.silenceWarningSec() != null) {
            e.setSilenceWarningSec(r.silenceWarningSec());
        }
        if (r.reconnectGraceSec() != null) {
            e.setReconnectGraceSec(r.reconnectGraceSec());
        }
        if (r.topicIds() != null) {
            e.setTopicIds(emptyToNull(r.topicIds()));
        }
        if (r.bloomLevels() != null) {
            e.setBloomLevels(emptyToNull(r.bloomLevels()));
        }
        if (r.showQuestionText() != null) {
            e.setShowQuestionText(r.showQuestionText());
        }
        validateConfig(e);
    }

    private Language courseLanguage(UUID courseId) {
        List<String> lang = jdbc.queryForList("select default_language from courses where course_id = :c",
                new MapSqlParameterSource("c", courseId), String.class);
        return lang.isEmpty() ? Language.VI : Language.valueOf(lang.getFirst());
    }

    private static int orDefault(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> List<T> emptyToNull(List<T> list) {
        List<T> clean = list == null ? List.of() : list.stream().filter(java.util.Objects::nonNull).distinct().toList();
        return clean.isEmpty() ? null : clean;
    }
}
