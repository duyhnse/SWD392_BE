package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamDtos.AddStudentsReport;
import swd392.group6.AIVES.exam.ExamDtos.ExamDetail;
import swd392.group6.AIVES.exam.ExamDtos.GeneratedQuestion;
import swd392.group6.AIVES.exam.ExamDtos.GeneratedSession;
import swd392.group6.AIVES.exam.ExamDtos.GenerationResult;
import swd392.group6.AIVES.exam.ExamDtos.RetakeRequest;
import swd392.group6.AIVES.exam.ExamDtos.RetakeResult;
import swd392.group6.AIVES.exam.ExamDtos.SessionRow;
import swd392.group6.AIVES.questionbank.QuestionBankApi.PublishedQuestion;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.User;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Question-set generation, lifecycle, retakes, results release and the lượt thi list (15 §2–§3, 07 §2). */
@Service
@RequiredArgsConstructor
@Transactional
class ExamSessionService {

    private final VivaExamRepository exams;
    private final BlueprintItemRepository blueprintItems;
    private final ExamAccess access;
    private final ExamQueries queries;
    private final ExamStatusRefresher refresher;
    private final VivaExamService examService;
    private final ExamStudentService studentService;
    private final NamedParameterJdbcTemplate jdbc;
    private final CourseAccessApi courseAccess;
    private final Clock clock;

    private record Examinee(UUID studentId, String username, String studentCode, int seqNo) {
    }

    GenerationResult generate(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw ApiException.conflict("EXAM_NOT_DRAFT", "Question sets can only be generated for a DRAFT exam");
        }
        if (!exam.getWindowEnd().isAfter(clock.instant())) {
            throw ApiException.unprocessable("INVALID_WINDOW", "The exam window has already ended");
        }
        List<Examinee> examinees = jdbc.query("""
                        select v.student_id, u.username, u.student_code, v.seq_no
                        from viva_exam_students v join users u on u.user_id = v.student_id
                        where v.viva_exam_id = :e order by v.seq_no""", ExamQueries.params(examId),
                (rs, i) -> new Examinee(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4)));
        if (examinees.isEmpty()) {
            throw ApiException.unprocessable("NO_STUDENTS", "Add students before generating question sets");
        }

        List<BlueprintItem> items = blueprintItems.findByVivaExamIdOrderBySortOrder(examId);
        List<QuestionSelector.Row> rows;
        if (items.isEmpty()) {
            rows = List.of(new QuestionSelector.Row(null, null, exam.getMainQuestionCount()));
        } else {
            int total = items.stream().mapToInt(BlueprintItem::getQuestionCount).sum();
            if (total != exam.getMainQuestionCount()) {
                throw VivaExamService.blueprintMismatch(total, exam.getMainQuestionCount());
            }
            rows = items.stream().map(b -> new QuestionSelector.Row(b.getTopicId(), b.getBloomLevel(), b.getQuestionCount()))
                    .toList();
        }

        List<PublishedQuestion> pool = queries.pool(exam, !items.isEmpty());
        Map<UUID, PublishedQuestion> poolById = new HashMap<>();
        pool.forEach(q -> poolById.put(q.questionId(), q));
        List<QuestionSelector.Candidate> candidates = pool.stream()
                .map(q -> new QuestionSelector.Candidate(q.questionId(), q.topicId(), q.bloomLevel())).toList();

        Map<UUID, Set<UUID>> previouslyReceived = previouslyReceived(exam, examinees);
        List<QuestionSelector.Examinee> order = examinees.stream()
                .map(s -> new QuestionSelector.Examinee(s.studentId(), previouslyReceived.getOrDefault(s.studentId(), Set.of())))
                .toList();
        QuestionSelector.Result result = QuestionSelector.select(candidates, rows, order, seed(examId));
        if (result.failed()) {
            throw new PoolTooSmallException(result.shortages());
        }

        MapSqlParameterSource p = ExamQueries.params(examId);
        jdbc.update("delete from session_questions where session_id in (select session_id from exam_sessions where viva_exam_id = :e)", p);
        jdbc.update("delete from exam_sessions where viva_exam_id = :e", p);
        Timestamp now = Timestamp.from(clock.instant());
        List<GeneratedSession> sessions = new ArrayList<>();
        for (Examinee s : examinees) {
            UUID sessionId = UUID.randomUUID();
            jdbc.update("""
                    insert into exam_sessions (session_id, viva_exam_id, course_id, student_id, examiner_id, status, created_at)
                    values (:id, :e, :c, :s, :x, 'SCHEDULED', :now)""", new MapSqlParameterSource("id", sessionId)
                    .addValue("e", examId).addValue("c", exam.getCourseId()).addValue("s", s.studentId())
                    .addValue("x", exam.getExaminerId()).addValue("now", now));
            List<GeneratedQuestion> questions = new ArrayList<>();
            int orderNo = 1;
            for (UUID questionId : result.assignments().get(s.studentId())) {
                UUID sqId = UUID.randomUUID();
                jdbc.update("""
                        insert into session_questions (session_question_id, session_id, question_id, order_no, status)
                        values (:id, :s, :q, :o, 'PENDING')""", new MapSqlParameterSource("id", sqId)
                        .addValue("s", sessionId).addValue("q", questionId).addValue("o", orderNo));
                PublishedQuestion q = poolById.get(questionId);
                questions.add(new GeneratedQuestion(sqId, questionId, orderNo, q.topicId(), q.bloomLevel(), q.content()));
                orderNo++;
            }
            sessions.add(new GeneratedSession(sessionId, s.studentId(), s.username(), s.studentCode(), s.seqNo(), questions));
        }
        exam.setStatus(ExamStatus.READY);
        exam.setUpdatedAt(clock.instant());
        exams.saveAndFlush(exam);
        return new GenerationResult(exam.getStatus(), sessions, result.warnings());
    }

    ExamDetail reset(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        if (exam.getStatus() != ExamStatus.READY) {
            throw ApiException.conflict("EXAM_NOT_READY", "Only a READY exam can be reset to DRAFT");
        }
        if (queries.anySessionStarted(examId)) {
            throw ApiException.conflict("SESSION_ALREADY_STARTED", "A student already started this exam");
        }
        MapSqlParameterSource p = ExamQueries.params(examId);
        jdbc.update("delete from session_questions where session_id in (select session_id from exam_sessions where viva_exam_id = :e)", p);
        jdbc.update("delete from exam_sessions where viva_exam_id = :e", p);
        exam.setStatus(ExamStatus.DRAFT);
        return save(exam);
    }

    /** READY → OPEN now; an exam opened early starts its window now. */
    ExamDetail open(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        if (exam.getStatus() != ExamStatus.READY) {
            throw ApiException.conflict("EXAM_NOT_READY", "Only a READY exam can be opened (status " + exam.getStatus() + ")");
        }
        Instant now = clock.instant();
        if (now.isBefore(exam.getWindowStart())) {
            exam.setWindowStart(now);
        }
        exam.setStatus(ExamStatus.OPEN);
        return save(exam);
    }

    /** OPEN → CLOSED; lượt thi never started become CANCELLED / NO_SHOW (AC-C7), started ones continue. */
    ExamDetail close(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        if (exam.getStatus() != ExamStatus.OPEN) {
            throw ApiException.conflict("EXAM_NOT_OPEN", "Only an OPEN exam can be closed (status " + exam.getStatus() + ")");
        }
        Instant now = clock.instant();
        if (now.isBefore(exam.getWindowEnd()) && now.isAfter(exam.getWindowStart())) {
            exam.setWindowEnd(now);
        }
        exam.setStatus(ExamStatus.CLOSED);
        refresher.markNoShows(examId);
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
        if (queries.anySessionInProgress(examId)) {
            throw ApiException.conflict("SESSION_IN_PROGRESS", "A student is taking this exam right now");
        }
        jdbc.update("""
                update exam_sessions set status = 'CANCELLED', cancel_reason = 'EXAM_CANCELLED'
                where viva_exam_id = :e and status = 'SCHEDULED'""", ExamQueries.params(examId));
        exam.setStatus(ExamStatus.CANCELLED);
        exam.setCancelReason(reason.trim());
        return save(exam);
    }

    /** New DRAFT buổi thi with the same course, config, blueprint and pool (15 §2.3, D30). */
    RetakeResult retake(UUID examId, RetakeRequest r, User user) {
        refresher.refreshDue();
        VivaExam source = access.write(examId, user);
        Instant now = clock.instant();
        VivaExam e = new VivaExam();
        e.setId(UUID.randomUUID());
        e.setCourseId(source.getCourseId());
        e.setTitle(r.title() != null && !r.title().isBlank() ? r.title().trim() : retakeTitle(source.getTitle()));
        e.setDescription(source.getDescription());
        e.setInstructions(source.getInstructions());
        e.setLocation(source.getLocation());
        e.setCreatedBy(user.getUserId());
        e.setExaminerId(source.getExaminerId());
        e.setWindowStart(r.windowStart());
        e.setWindowEnd(r.windowEnd());
        e.setLanguage(source.getLanguage());
        e.setMainQuestionCount(source.getMainQuestionCount());
        e.setMaxFollowupsPerQuestion(source.getMaxFollowupsPerQuestion());
        e.setTimeLimitPerStudentSec(source.getTimeLimitPerStudentSec());
        e.setAnswerTimeLimitSec(source.getAnswerTimeLimitSec());
        e.setSilenceWarningSec(source.getSilenceWarningSec());
        e.setReconnectGraceSec(source.getReconnectGraceSec());
        e.setTopicIds(source.getTopicIds());
        e.setBloomLevels(source.getBloomLevels());
        e.setSelectionStrategy(source.getSelectionStrategy());
        e.setShowQuestionText(source.isShowQuestionText());
        e.setQuestionPoolMode(source.getQuestionPoolMode());
        e.setRetakeOfVivaExamId(source.getId());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        if (!courseAccess.isLecturerOf(e.getCourseId(), e.getExaminerId())) {
            // The original examiner may have been unassigned since; the caller examines the retake instead.
            e.setExaminerId(user.getUserId());
        }
        examService.validateConfig(e);
        exams.saveAndFlush(e);

        List<BlueprintItem> copies = blueprintItems.findByVivaExamIdOrderBySortOrder(source.getId()).stream()
                .map(b -> new BlueprintItem(UUID.randomUUID(), e.getId(), b.getTopicId(), b.getBloomLevel(),
                        b.getQuestionCount(), b.getSortOrder()))
                .toList();
        blueprintItems.saveAllAndFlush(copies);
        jdbc.update("""
                insert into viva_exam_questions (viva_exam_id, question_id)
                select :n, question_id from viva_exam_questions where viva_exam_id = :e""",
                ExamQueries.params(source.getId()).addValue("n", e.getId()));

        Set<UUID> original = new HashSet<>(jdbc.queryForList("""
                select student_id from viva_exam_students where viva_exam_id = :e
                union select student_id from exam_sessions where viva_exam_id = :e""",
                ExamQueries.params(source.getId()), UUID.class));
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

    List<SessionRow> sessions(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.read(examId, user);
        Instant now = clock.instant();
        return jdbc.query("""
                        select s.session_id, s.student_id, u.username, u.full_name, u.student_code, v.seq_no, s.status,
                               s.cancel_reason, s.end_reason, s.started_at, s.deadline_at, s.ended_at,
                               g.evaluation_id, g.status as evaluation_status, g.final_total_score
                        from exam_sessions s join users u on u.user_id = s.student_id
                        left join viva_exam_students v on v.viva_exam_id = s.viva_exam_id and v.student_id = s.student_id
                        left join grade_evaluations g on g.session_id = s.session_id
                        where s.viva_exam_id = :e order by v.seq_no nulls last, u.username""",
                ExamQueries.params(examId),
                (rs, i) -> new SessionRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5), (Integer) rs.getObject(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), SessionStage.of(rs.getString(7), rs.getString(8), exam.getStatus(),
                        exam.getWindowStart(), exam.getWindowEnd(), now),
                        ExamQueries.instant(rs.getTimestamp(10)), ExamQueries.instant(rs.getTimestamp(11)),
                        ExamQueries.instant(rs.getTimestamp(12)), rs.getObject(13, UUID.class), rs.getString(14),
                        rs.getBigDecimal(15)));
    }

    /** Deterministic seed from the buổi thi id (07 §2, AC-E7). */
    static long seed(UUID examId) {
        return examId.getMostSignificantBits() ^ examId.getLeastSignificantBits();
    }

    /**
     * For a retake: questions each student already received in the original buổi thi (and the ones before it).
     */
    private Map<UUID, Set<UUID>> previouslyReceived(VivaExam exam, List<Examinee> examinees) {
        List<UUID> ancestors = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        UUID current = exam.getRetakeOfVivaExamId();
        while (current != null && seen.add(current)) {
            ancestors.add(current);
            List<UUID> parent = jdbc.queryForList("select retake_of_viva_exam_id from viva_exams where viva_exam_id = :e",
                    ExamQueries.params(current), UUID.class);
            current = parent.isEmpty() ? null : parent.getFirst();
        }
        Map<UUID, Set<UUID>> received = new HashMap<>();
        if (ancestors.isEmpty()) {
            return received;
        }
        jdbc.query("""
                        select s.student_id, q.question_id from exam_sessions s
                        join session_questions q on q.session_id = s.session_id
                        where s.viva_exam_id in (:ids) and s.student_id in (:students)""",
                new MapSqlParameterSource("ids", ancestors)
                        .addValue("students", examinees.stream().map(Examinee::studentId).toList()),
                rs -> {
                    received.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashSet<>()).add(rs.getObject(2, UUID.class));
                });
        return received;
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
