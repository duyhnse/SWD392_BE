package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamDtos.AddStudentsReport;
import swd392.group6.AIVES.exam.ExamDtos.AttemptRow;
import swd392.group6.AIVES.exam.ExamDtos.ExamDetail;
import swd392.group6.AIVES.exam.ExamDtos.PoolCheck;
import swd392.group6.AIVES.exam.ExamDtos.RetakeRequest;
import swd392.group6.AIVES.exam.ExamDtos.RetakeResult;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.User;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Buổi thi lifecycle (15 §2.1, D47, D48): publish → open → close, cancel, retakes, results release and the roster
 * with attempts. Questions are not chosen here — every student draws theirs at check-in ({@link CheckInService}).
 */
@Service
@RequiredArgsConstructor
@Transactional
class ExamLifecycleService {

    private final VivaExamRepository exams;
    private final ExamTemplateRepository templates;
    private final ExamTemplateItemRepository items;
    private final ExamTemplateService templateService;
    private final ExamAccess access;
    private final ExamQueries queries;
    private final ExamStatusRefresher refresher;
    private final VivaExamService examService;
    private final ExamStudentService studentService;
    private final NamedParameterJdbcTemplate jdbc;
    private final CourseAccessApi courseAccess;
    private final Clock clock;

    /** Can the template's pool serve every row (D48)? Same check as publish, without changing anything. */
    PoolCheck poolCheck(UUID examId, User user) {
        refresher.refreshDue();
        return poolCheck(templateOf(access.read(examId, user)));
    }

    PoolCheck poolCheck(ExamTemplate t) {
        List<ExamTemplateItem> rows = items.findByTemplateIdOrderBySortOrder(t.getId());
        if (rows.isEmpty()) {
            throw ApiException.unprocessable("TEMPLATE_ITEMS_REQUIRED", "The exam template has no rows yet");
        }
        List<QuestionSelector.RowShortage> shortages = QuestionSelector.shortages(queries.candidates(t), ExamQueries.rows(rows));
        return new PoolCheck(shortages.isEmpty(), shortages);
    }

    /** DRAFT → READY: students can see it and check in once the window opens; the template gets locked (D49). */
    ExamDetail publish(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw ApiException.conflict("EXAM_NOT_DRAFT", "Only a DRAFT exam can be published");
        }
        examService.validate(exam);
        if (!exam.getCheckinClosesAt().isAfter(clock.instant())) {
            throw ApiException.unprocessable("INVALID_WINDOW", "The check-in window has already ended");
        }
        if (queries.studentCount(examId) == 0) {
            throw ApiException.unprocessable("NO_STUDENTS", "Add students before publishing");
        }
        ExamTemplate template = templateOf(exam);
        PoolCheck check = poolCheck(template);
        if (!check.sufficient()) {
            throw new PoolTooSmallException(check.shortages());
        }
        templateService.lock(template);
        // A window that already started opens at once (same rule as ExamStatusRefresher).
        exam.setStatus(clock.instant().isBefore(exam.getCheckinOpensAt()) ? ExamStatus.READY : ExamStatus.OPEN);
        return save(exam);
    }

    /** READY → DRAFT while nobody checked in. The template stays locked (duplicate it to change it). */
    ExamDetail unpublish(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        if (exam.getStatus() != ExamStatus.READY && exam.getStatus() != ExamStatus.OPEN) {
            throw ApiException.conflict("EXAM_NOT_READY", "Only a published exam can be taken back to DRAFT");
        }
        if (queries.anyAttempt(examId)) {
            throw ApiException.conflict("ATTEMPT_EXISTS", "A student already checked in");
        }
        exam.setStatus(ExamStatus.DRAFT);
        return save(exam);
    }

    /** READY → OPEN now; an exam opened early starts its check-in window now. */
    ExamDetail open(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        if (exam.getStatus() != ExamStatus.READY) {
            throw ApiException.conflict("EXAM_NOT_READY", "Only a READY exam can be opened (status " + exam.getStatus() + ")");
        }
        Instant now = clock.instant();
        if (now.isBefore(exam.getCheckinOpensAt())) {
            exam.setCheckinOpensAt(now);
        }
        exam.setStatus(ExamStatus.OPEN);
        return save(exam);
    }

    /** OPEN → CLOSED: no more check-ins; running attempts continue to their own deadline (D47). */
    ExamDetail close(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        if (exam.getStatus() != ExamStatus.OPEN) {
            throw ApiException.conflict("EXAM_NOT_OPEN", "Only an OPEN exam can be closed (status " + exam.getStatus() + ")");
        }
        Instant now = clock.instant();
        if (now.isBefore(exam.getCheckinClosesAt()) && now.isAfter(exam.getCheckinOpensAt())) {
            exam.setCheckinClosesAt(now);
        }
        exam.setStatus(ExamStatus.CLOSED);
        return save(exam);
    }

    ExamDetail cancel(UUID examId, String reason, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        if (reason == null || reason.isBlank()) {
            throw ApiException.unprocessable("REASON_REQUIRED", "A reason is required to cancel an exam");
        }
        if (exam.getStatus() == ExamStatus.CLOSED || exam.getStatus() == ExamStatus.CANCELLED) {
            throw ApiException.conflict("EXAM_NOT_CANCELLABLE", "A " + exam.getStatus() + " exam cannot be cancelled");
        }
        if (queries.anyAttemptRunning(examId)) {
            throw ApiException.conflict("ATTEMPT_IN_PROGRESS", "A student is taking this exam right now");
        }
        exam.setStatus(ExamStatus.CANCELLED);
        exam.setCancelReason(reason.trim());
        return save(exam);
    }

    /** New DRAFT buổi thi with the same đề thi (15 §2.3, D30): same knowledge, different questions. */
    RetakeResult retake(UUID examId, RetakeRequest r, User user) {
        refresher.refreshDue();
        VivaExam source = access.write(examId, user);
        Instant now = clock.instant();
        VivaExam e = new VivaExam();
        e.setId(UUID.randomUUID());
        e.setCourseId(source.getCourseId());
        e.setTemplateId(source.getTemplateId());
        e.setTitle(r.title() != null && !r.title().isBlank() ? r.title().trim() : retakeTitle(source.getTitle()));
        e.setDescription(source.getDescription());
        e.setInstructions(source.getInstructions());
        e.setLocation(source.getLocation());
        e.setCreatedBy(user.getUserId());
        e.setExaminerId(source.getExaminerId());
        e.setCheckinOpensAt(r.checkinOpensAt());
        e.setCheckinClosesAt(r.checkinClosesAt());
        e.setReconnectGraceSec(source.getReconnectGraceSec());
        e.setMaxDisconnects(source.getMaxDisconnects());
        e.setMaxFrozenSec(source.getMaxFrozenSec());
        e.setReplaceMainAfterSec(source.getReplaceMainAfterSec());
        e.setRetakeOfVivaExamId(source.getId());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        if (!courseAccess.isLecturerOf(e.getCourseId(), e.getExaminerId())) {
            // The original examiner may have been unassigned since; the caller examines the retake instead.
            e.setExaminerId(user.getUserId());
        }
        examService.validate(e);
        exams.saveAndFlush(e);

        Set<UUID> original = new HashSet<>(jdbc.queryForList(
                "select student_id from viva_exam_students where viva_exam_id = :e", ExamQueries.params(source.getId()),
                UUID.class));
        AddStudentsReport students = studentService.addResolved(e.getId(),
                ExamStudentService.tokens(r.studentCodes(), true), ExamStudentService.tokens(r.usernames(), false),
                original, user.getUserId());
        return new RetakeResult(queries.detail(e), students);
    }

    ExamDetail setResultsReleased(UUID examId, boolean released, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        exam.setResultsReleased(released);
        exam.setResultsReleasedAt(released ? clock.instant() : null);
        return save(exam);
    }

    /** The roster with each student's attempt (if any), stage and grading status. */
    List<AttemptRow> attempts(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.read(examId, user);
        Instant now = clock.instant();
        return jdbc.query("""
                        select v.student_id, u.username, u.full_name, u.student_code, v.seq_no, a.attempt_id, a.status,
                               a.end_reason, a.started_at, a.deadline_at, a.ended_at, a.disconnect_count, a.frozen_sec_total,
                               g.evaluation_id, g.status as evaluation_status, g.final_total_score
                        from viva_exam_students v join users u on u.user_id = v.student_id
                        left join exam_attempts a on a.viva_exam_id = v.viva_exam_id and a.student_id = v.student_id
                        left join grade_evaluations g on g.attempt_id = a.attempt_id
                        where v.viva_exam_id = :e order by v.seq_no""",
                ExamQueries.params(examId),
                (rs, i) -> new AttemptRow(rs.getObject("student_id", UUID.class), rs.getString("username"),
                        rs.getString("full_name"), rs.getString("student_code"), rs.getInt("seq_no"),
                        ExamStage.of(rs.getString("status"), exam.getStatus(), exam.getCheckinOpensAt(),
                                exam.getCheckinClosesAt(), now),
                        rs.getObject("attempt_id", UUID.class), rs.getString("status"), rs.getString("end_reason"),
                        ExamQueries.instant(rs.getTimestamp("started_at")), ExamQueries.instant(rs.getTimestamp("deadline_at")),
                        ExamQueries.instant(rs.getTimestamp("ended_at")), rs.getInt("disconnect_count"),
                        rs.getInt("frozen_sec_total"), rs.getObject("evaluation_id", UUID.class),
                        rs.getString("evaluation_status"), rs.getBigDecimal("final_total_score")));
    }

    private ExamTemplate templateOf(VivaExam exam) {
        return templates.findById(exam.getTemplateId()).orElseThrow();
    }

    private static String retakeTitle(String title) {
        String t = title + " – Thi lại";
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    private ExamDetail save(VivaExam exam) {
        exam.setUpdatedAt(clock.instant());
        return queries.detail(exams.saveAndFlush(exam));
    }
}
