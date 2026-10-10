package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.exam.ExamDtos.CreateExamRequest;
import swd392.group6.AIVES.exam.ExamDtos.ExamDetail;
import swd392.group6.AIVES.exam.ExamDtos.ExamSummary;
import swd392.group6.AIVES.exam.ExamDtos.UpdateExamRequest;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.User;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Buổi thi CRUD (D47, 15 §5.3). Lifecycle and retakes: {@link ExamLifecycleService}. */
@Service
@RequiredArgsConstructor
@Transactional
class VivaExamService {

    private final VivaExamRepository exams;
    private final ExamTemplateRepository templates;
    private final ExamAccess access;
    private final ExamQueries queries;
    private final ExamStatusRefresher refresher;
    private final CourseAccessApi courseAccess;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    PageResponse<ExamSummary> list(UUID courseId, User user, List<ExamStatus> statuses, String when, int page, int size) {
        courseAccess.requireRead(courseId, user);
        refresher.refreshDue();
        int p = Math.max(page, 0);
        int s = Math.clamp(size, 1, 100);
        StringBuilder where = new StringBuilder(" where e.course_id = :c");
        MapSqlParameterSource params = new MapSqlParameterSource("c", courseId)
                .addValue("now", Timestamp.from(clock.instant()));
        if (statuses != null && !statuses.isEmpty()) {
            where.append(" and e.status in (:st)");
            params.addValue("st", statuses.stream().map(Enum::name).toList());
        }
        if (when != null && !when.isBlank()) {
            where.append(switch (when.trim().toLowerCase(Locale.ROOT)) {
                case "upcoming" -> " and e.checkin_opens_at > :now";
                case "ongoing" -> " and e.checkin_opens_at <= :now and (e.checkin_closes_at >= :now or exists("
                        + "select 1 from exam_attempts a where a.viva_exam_id = e.viva_exam_id"
                        + " and a.status in ('IN_PROGRESS', 'INTERRUPTED')))";
                case "past" -> " and e.checkin_closes_at < :now and not exists(select 1 from exam_attempts a"
                        + " where a.viva_exam_id = e.viva_exam_id and a.status in ('IN_PROGRESS', 'INTERRUPTED'))";
                default -> throw ApiException.unprocessable("INVALID_FILTER", "when must be upcoming, ongoing or past");
            });
        }
        Long total = jdbc.queryForObject("select count(*) from viva_exams e" + where, params, Long.class);
        params.addValue("limit", s).addValue("offset", (long) p * s);
        List<ExamSummary> items = jdbc.query("""
                select e.viva_exam_id, e.display_no, e.course_id, e.exam_template_id, t.title as template_title, e.title, e.status,
                       e.checkin_opens_at, e.checkin_closes_at, e.results_released, e.retake_of_viva_exam_id, e.version,
                       (select coalesce(sum(i.question_count), 0) from exam_template_items i
                          where i.exam_template_id = e.exam_template_id) as questions,
                       (select coalesce(sum(i.question_count * i.seconds_per_question), 0) from exam_template_items i
                          where i.exam_template_id = e.exam_template_id) as duration,
                       (select count(*) from viva_exam_students v where v.viva_exam_id = e.viva_exam_id) as students,
                       (select count(*) from exam_attempts a where a.viva_exam_id = e.viva_exam_id) as checked_in,
                       (select count(*) from exam_attempts a where a.viva_exam_id = e.viva_exam_id
                          and a.status = 'COMPLETED') as completed,
                       (select count(*) from exam_attempts a join grade_evaluations g on g.attempt_id = a.attempt_id
                          where a.viva_exam_id = e.viva_exam_id and g.status = 'CONFIRMED') as confirmed
                from viva_exams e join exam_templates t on t.exam_template_id = e.exam_template_id""" + where
                        + " order by e.checkin_opens_at desc, e.title limit :limit offset :offset",
                params, (rs, i) -> new ExamSummary(rs.getObject("viva_exam_id", UUID.class), rs.getLong("display_no"),
                        rs.getObject("course_id", UUID.class), rs.getObject("exam_template_id", UUID.class),
                        rs.getString("template_title"), rs.getString("title"), ExamStatus.valueOf(rs.getString("status")),
                        ExamQueries.instant(rs.getTimestamp("checkin_opens_at")),
                        ExamQueries.instant(rs.getTimestamp("checkin_closes_at")), rs.getInt("questions"),
                        rs.getInt("duration"), rs.getLong("students"), rs.getLong("checked_in"), rs.getLong("completed"),
                        rs.getLong("confirmed"), rs.getBoolean("results_released"),
                        rs.getObject("retake_of_viva_exam_id", UUID.class), rs.getInt("version")));
        return new PageResponse<>(items, p, s, total == null ? 0 : total);
    }

    ExamDetail create(UUID courseId, CreateExamRequest r, User user) {
        courseAccess.requireWrite(courseId, user);
        Instant now = clock.instant();
        VivaExam e = new VivaExam();
        e.setId(UUID.randomUUID());
        e.setCourseId(courseId);
        e.setTemplateId(r.templateId());
        e.setTitle(r.title().trim());
        e.setDescription(blankToNull(r.description()));
        e.setInstructions(blankToNull(r.instructions()));
        e.setLocation(blankToNull(r.location()));
        e.setCreatedBy(user.getUserId());
        e.setExaminerId(r.examinerId() != null ? r.examinerId() : user.getUserId());
        e.setCheckinOpensAt(r.checkinOpensAt());
        e.setCheckinClosesAt(r.checkinClosesAt());
        if (r.reconnectGraceSec() != null) {
            e.setReconnectGraceSec(r.reconnectGraceSec());
        }
        if (r.maxDisconnects() != null) {
            e.setMaxDisconnects(r.maxDisconnects());
        }
        if (r.maxFrozenSec() != null) {
            e.setMaxFrozenSec(r.maxFrozenSec());
        }
        if (r.replaceMainAfterSec() != null) {
            e.setReplaceMainAfterSec(r.replaceMainAfterSec());
        }
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        validate(e);
        return queries.detail(exams.saveAndFlush(e));
    }

    ExamDetail get(UUID examId, User user) {
        refresher.refreshDue();
        return queries.detail(access.read(examId, user));
    }

    /**
     * DRAFT: everything. READY / OPEN: texts and the closing time (students may already check in). Never after
     * CLOSED / CANCELLED.
     */
    ExamDetail update(UUID examId, UpdateExamRequest r, User user) {
        refresher.refreshDue();
        VivaExam e = access.write(examId, user);
        if (!r.version().equals(e.getVersion())) {
            throw ExamTemplateService.versionConflict();
        }
        switch (e.getStatus()) {
            case DRAFT -> applyDraftFields(e, r);
            case READY, OPEN -> {
                if (r.touchesFrozenFields()) {
                    throw frozen();
                }
            }
            default -> throw ApiException.conflict("EXAM_NOT_EDITABLE", "A closed or cancelled exam cannot be edited");
        }
        if (r.checkinClosesAt() != null) {
            e.setCheckinClosesAt(r.checkinClosesAt());
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
        validate(e);
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
        jdbc.update("delete from viva_exam_students where viva_exam_id = :e", p);
        exams.delete(e);
    }

    /** 07 §3 rules for a buổi thi, 422 with a specific code per field. */
    void validate(VivaExam e) {
        if (!e.getCheckinClosesAt().isAfter(e.getCheckinOpensAt())) {
            throw ApiException.unprocessable("INVALID_WINDOW", "checkinClosesAt must be after checkinOpensAt");
        }
        if (e.getReconnectGraceSec() < 10 || e.getReconnectGraceSec() > 600) {
            throw ApiException.unprocessable("INVALID_CONFIG", "reconnectGraceSec must be 10–600");
        }
        if (e.getMaxDisconnects() < 0 || e.getMaxDisconnects() > 20) {
            throw ApiException.unprocessable("INVALID_CONFIG", "maxDisconnects must be 0–20");
        }
        if (e.getMaxFrozenSec() < 0 || e.getMaxFrozenSec() > 1800) {
            throw ApiException.unprocessable("INVALID_CONFIG", "maxFrozenSec must be 0–1800");
        }
        if (e.getReplaceMainAfterSec() < 0 || e.getReplaceMainAfterSec() > 600) {
            throw ApiException.unprocessable("INVALID_CONFIG", "replaceMainAfterSec must be 0–600");
        }
        if (!courseAccess.isLecturerOf(e.getCourseId(), e.getExaminerId())) {
            throw ApiException.unprocessable("INVALID_EXAMINER", "The examiner must be a lecturer assigned to the course");
        }
        ExamTemplate t = templates.findById(e.getTemplateId())
                .filter(x -> x.getCourseId().equals(e.getCourseId()))
                .orElseThrow(() -> ApiException.unprocessable("TEMPLATE_NOT_IN_COURSE",
                        "The exam template does not exist in this course"));
        if (t.isArchived() && e.getStatus() == ExamStatus.DRAFT) {
            throw ApiException.unprocessable("TEMPLATE_ARCHIVED", "The exam template is archived");
        }
    }

    static ApiException frozen() {
        return ApiException.conflict("EXAM_CONFIG_FROZEN",
                "Students may check in: only title, description, instructions, location and the closing time can change");
    }

    private static void applyDraftFields(VivaExam e, UpdateExamRequest r) {
        if (r.templateId() != null) {
            e.setTemplateId(r.templateId());
        }
        if (r.examinerId() != null) {
            e.setExaminerId(r.examinerId());
        }
        if (r.checkinOpensAt() != null) {
            e.setCheckinOpensAt(r.checkinOpensAt());
        }
        if (r.reconnectGraceSec() != null) {
            e.setReconnectGraceSec(r.reconnectGraceSec());
        }
        if (r.maxDisconnects() != null) {
            e.setMaxDisconnects(r.maxDisconnects());
        }
        if (r.maxFrozenSec() != null) {
            e.setMaxFrozenSec(r.maxFrozenSec());
        }
        if (r.replaceMainAfterSec() != null) {
            e.setReplaceMainAfterSec(r.replaceMainAfterSec());
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
