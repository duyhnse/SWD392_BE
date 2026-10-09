package swd392.group6.AIVES.grading.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.exam.ExamApi.SessionInfo;
import swd392.group6.AIVES.grading.GradingApi;
import swd392.group6.AIVES.grading.internal.GradingDtos.ChangeDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.CriterionDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.CriterionFinal;
import swd392.group6.AIVES.grading.internal.GradingDtos.EvaluationDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.EvaluationSummaryDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.IncompleteThread;
import swd392.group6.AIVES.grading.internal.GradingDtos.QuestionRef;
import swd392.group6.AIVES.grading.internal.GradingDtos.RubricRef;
import swd392.group6.AIVES.grading.internal.GradingDtos.StudentRef;
import swd392.group6.AIVES.grading.internal.GradingDtos.ThreadDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.TurnDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.UpdateEvaluationRequest;
import swd392.group6.AIVES.grading.internal.GradingDtos.UpdateThreadRequest;
import swd392.group6.AIVES.grading.internal.GradingSupport.EvaluationContext;
import swd392.group6.AIVES.interview.InterviewApi;
import swd392.group6.AIVES.interview.InterviewApi.ThreadInfo;
import swd392.group6.AIVES.questionbank.QuestionBankApi;
import swd392.group6.AIVES.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lecturer review of evaluations (06 §4 steps 6–9, BR-G1..G7). */
@Service
@RequiredArgsConstructor
@Transactional
class EvaluationService {

    private final GradingSupport support;
    private final GradingApi gradingApi;
    private final GradeEvaluationRepository evaluations;
    private final QuestionGradeRepository grades;
    private final CriterionScoreRepository criterionScores;
    private final GradeChangeLogRepository changeLog;
    private final GradeDisputeRepository disputes;
    private final InterviewApi interviewApi;
    private final QuestionBankApi questionBankApi;
    private final Clock clock;

    record Created(EvaluationDto evaluation, boolean created) {
    }

    public Created create(UUID sessionId, User user) {
        SessionInfo session = support.requireSession(sessionId);
        support.requireCourseWrite(session.courseId(), user);
        GradingApi.CreatedEvaluation result = gradingApi.createEvaluation(sessionId, GradingApi.Mode.MANUAL);
        GradeEvaluation evaluation = support.requireEvaluation(result.evaluationId());
        return new Created(toDto(evaluation, session), result.created());
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationSummaryDto> listForExam(UUID vivaExamId, User user, int page, int size) {
        support.requireExamRead(vivaExamId, user);
        List<GradingSupport.SessionRow> sessions = support.sessionsOfExam(vivaExamId);
        Map<UUID, GradeEvaluation> bySession = evaluations
                .findBySessionIdIn(sessions.stream().map(GradingSupport.SessionRow::sessionId).toList()).stream()
                .collect(Collectors.toMap(GradeEvaluation::getSessionId, Function.identity()));
        List<UUID> evaluationIds = bySession.values().stream().map(GradeEvaluation::getEvaluationId).toList();
        Map<UUID, List<QuestionGrade>> gradesByEvaluation = evaluationIds.isEmpty() ? Map.of()
                : grades.findByEvaluationIdIn(evaluationIds).stream()
                .collect(Collectors.groupingBy(QuestionGrade::getEvaluationId));
        Set<UUID> withOpenDispute = evaluationIds.isEmpty() ? Set.of()
                : disputes.findByEvaluationIdInOrderByCreatedAtDesc(evaluationIds).stream()
                .filter(d -> d.getStatus() == DisputeStatus.OPEN).map(GradeDispute::getEvaluationId)
                .collect(Collectors.toSet());

        List<EvaluationSummaryDto> rows = sessions.stream().map(s -> {
            GradeEvaluation e = bySession.get(s.sessionId());
            List<QuestionGrade> threads = e == null ? List.of() : gradesByEvaluation.getOrDefault(e.getEvaluationId(), List.of());
            return new EvaluationSummaryDto(s.sessionId(), new StudentRef(s.studentId(), s.fullName(), s.studentCode()),
                    s.status(), s.endedAt(), e == null ? null : e.getEvaluationId(), e == null ? null : e.getStatus().name(),
                    e == null ? null : e.getAiTotalScore(), e == null ? null : e.getFinalTotalScore(),
                    (int) threads.stream().filter(g -> g.getStatus() == QuestionGradeStatus.AI_FAILED).count(),
                    (int) threads.stream().filter(g -> g.getStatus() == QuestionGradeStatus.MISSING_DATA).count(),
                    e != null && withOpenDispute.contains(e.getEvaluationId()));
        }).toList();
        return page(rows, page, size);
    }

    @Transactional(readOnly = true)
    public EvaluationDto get(UUID evaluationId, User user) {
        EvaluationContext ctx = support.evaluationForRead(evaluationId, user);
        return toDto(ctx.evaluation(), ctx.session());
    }

    public EvaluationDto updateThread(UUID evaluationId, UUID gradeId, UpdateThreadRequest request, User user) {
        EvaluationContext ctx = writable(evaluationId, user, request.version());
        GradeEvaluation evaluation = ctx.evaluation();
        QuestionGrade grade = requireGrade(evaluation, gradeId);
        List<CriterionScore> criteria = criterionScores.findByQuestionGradeIdIn(List.of(gradeId));
        Map<UUID, CriterionScore> byCriterion = criteria.stream()
                .collect(Collectors.toMap(CriterionScore::getCriterionId, Function.identity()));
        Instant now = clock.instant();

        List<CriterionFinal> finals = request.criteria() == null ? List.of() : request.criteria();
        for (CriterionFinal item : finals) {
            CriterionScore score = byCriterion.get(item.criterionId());
            if (score == null) {
                throw ApiException.unprocessable("UNKNOWN_CRITERION",
                        "Criterion " + item.criterionId() + " is not part of this thread's rubric");
            }
            if (!ScoreCalculator.isValidFinalScore(item.finalScore(), score.getMaxScore())) {
                throw ApiException.unprocessable("SCORE_OUT_OF_RANGE", "Score " + item.finalScore().toPlainString()
                        + " must be between 0 and " + score.getMaxScore().toPlainString() + " in steps of 0.25");
            }
        }
        for (CriterionFinal item : finals) {
            setFinal(evaluation, grade, byCriterion.get(item.criterionId()), item.finalScore(), user, now);
        }
        if (request.lecturerComment() != null) {
            String comment = request.lecturerComment().isBlank() ? null : request.lecturerComment().strip();
            if (!Objects.equals(comment, grade.getLecturerComment())) {
                log(evaluation, grade, null, "lecturer_comment", grade.getLecturerComment(), comment, user, now);
                grade.setLecturerComment(comment);
            }
        }
        if (request.includeInTotal() != null && request.includeInTotal() != grade.isIncludeInTotal()) {
            log(evaluation, grade, null, "include_in_total", String.valueOf(grade.isIncludeInTotal()),
                    String.valueOf(request.includeInTotal()), user, now);
            grade.setIncludeInTotal(request.includeInTotal());
        }
        recompute(grade, criteria, user, now);
        return save(evaluation, ctx.session(), now);
    }

    public EvaluationDto acceptAi(UUID evaluationId, UUID gradeId, Integer version, User user) {
        EvaluationContext ctx = writable(evaluationId, user, version);
        QuestionGrade grade = requireGrade(ctx.evaluation(), gradeId);
        List<CriterionScore> criteria = criterionScores.findByQuestionGradeIdIn(List.of(gradeId));
        if (criteria.stream().noneMatch(c -> c.getAiScore() != null)) {
            throw ApiException.conflict("AI_SCORE_NOT_AVAILABLE", "This thread has no AI suggestion to accept");
        }
        Instant now = clock.instant();
        copyAi(ctx.evaluation(), grade, criteria, user, now);
        return save(ctx.evaluation(), ctx.session(), now);
    }

    public EvaluationDto acceptAllAi(UUID evaluationId, Integer version, User user) {
        EvaluationContext ctx = writable(evaluationId, user, version);
        List<QuestionGrade> all = grades.findByEvaluationId(evaluationId);
        Map<UUID, List<CriterionScore>> criteria = criteriaOf(all);
        Instant now = clock.instant();
        for (QuestionGrade grade : all) {
            List<CriterionScore> scores = criteria.getOrDefault(grade.getQuestionGradeId(), List.of());
            if (grade.getAiScore() != null && scores.stream().allMatch(c -> c.getAiScore() != null)) {
                copyAi(ctx.evaluation(), grade, scores, user, now);
            }
        }
        return save(ctx.evaluation(), ctx.session(), now);
    }

    public EvaluationDto updateEvaluation(UUID evaluationId, UpdateEvaluationRequest request, User user) {
        EvaluationContext ctx = writable(evaluationId, user, request.version());
        GradeEvaluation evaluation = ctx.evaluation();
        Instant now = clock.instant();
        String comment = request.lecturerComment() == null || request.lecturerComment().isBlank()
                ? null : request.lecturerComment().strip();
        if (!Objects.equals(comment, evaluation.getLecturerComment())) {
            log(evaluation, null, null, "evaluation_comment", evaluation.getLecturerComment(), comment, user, now);
            evaluation.setLecturerComment(comment);
        }
        return save(evaluation, ctx.session(), now);
    }

    public EvaluationDto confirm(UUID evaluationId, Integer version, User user) {
        EvaluationContext ctx = writable(evaluationId, user, version);
        GradeEvaluation evaluation = ctx.evaluation();
        List<QuestionGrade> all = grades.findByEvaluationId(evaluationId);
        Map<UUID, List<CriterionScore>> criteria = criteriaOf(all);
        Map<UUID, Integer> orderNo = orderNumbers(ctx.session().sessionId());

        List<IncompleteThread> incomplete = all.stream()
                .filter(QuestionGrade::isIncludeInTotal)
                .filter(g -> criteria.getOrDefault(g.getQuestionGradeId(), List.of()).stream()
                        .anyMatch(c -> c.getFinalScore() == null))
                .map(g -> new IncompleteThread(g.getQuestionGradeId(), orderNo.getOrDefault(g.getSessionQuestionId(), 0)))
                .sorted(Comparator.comparingInt(IncompleteThread::orderNo))
                .toList();
        if (!incomplete.isEmpty()) {
            throw new GradesIncompleteException("Threads without final scores: "
                    + incomplete.stream().map(t -> String.valueOf(t.orderNo())).collect(Collectors.joining(", ")),
                    incomplete);
        }
        List<QuestionGrade> included = all.stream().filter(QuestionGrade::isIncludeInTotal).toList();
        if (included.isEmpty()) {
            throw new GradesIncompleteException("At least one thread must be included in the total", List.of());
        }
        Instant now = clock.instant();
        for (QuestionGrade grade : included) {
            recompute(grade, criteria.get(grade.getQuestionGradeId()), user, now);
        }
        BigDecimal total = ScoreCalculator.mean(included.stream().map(QuestionGrade::getFinalScore).toList());
        log(evaluation, null, null, "status", evaluation.getStatus().name(), EvaluationStatus.CONFIRMED.name(), user, now);
        if (evaluation.getFinalTotalScore() == null || evaluation.getFinalTotalScore().compareTo(total) != 0) {
            log(evaluation, null, null, "final_total_score", plain(evaluation.getFinalTotalScore()), plain(total), user, now);
        }
        evaluation.setStatus(EvaluationStatus.CONFIRMED);
        evaluation.setFinalTotalScore(total);
        evaluation.setConfirmedBy(user.getUserId());
        evaluation.setConfirmedAt(now);
        return save(evaluation, ctx.session(), now);
    }

    @Transactional(readOnly = true)
    public List<ChangeDto> history(UUID evaluationId, User user) {
        EvaluationContext ctx = support.evaluationForRead(evaluationId, user);
        List<GradeChangeLog> rows = changeLog.findByEvaluationIdOrderByChangedAtAscChangeIdAsc(evaluationId);
        Map<UUID, Integer> orderNo = orderNumbers(ctx.session().sessionId());
        Map<UUID, UUID> gradeToSessionQuestion = grades.findByEvaluationId(evaluationId).stream()
                .collect(Collectors.toMap(QuestionGrade::getQuestionGradeId, QuestionGrade::getSessionQuestionId));
        Map<UUID, GradingSupport.Student> people = support.students(
                rows.stream().map(GradeChangeLog::getChangedBy).collect(Collectors.toSet()));
        return rows.stream().map(r -> new ChangeDto(r.getChangeId(), r.getQuestionGradeId(),
                r.getQuestionGradeId() == null ? null : orderNo.get(gradeToSessionQuestion.get(r.getQuestionGradeId())),
                r.getCriterionScoreId(), r.getField(), r.getOldValue(), r.getNewValue(), r.getChangedBy(),
                people.containsKey(r.getChangedBy()) ? people.get(r.getChangedBy()).fullName() : null,
                r.getChangedAt())).toList();
    }

    // ----- helpers ------------------------------------------------------------------------------------------------

    /** Lecturer write access + BR-G6 (CONFIRMED is read-only; DISPUTED is editable again) + optimistic lock. */
    private EvaluationContext writable(UUID evaluationId, User user, Integer expectedVersion) {
        EvaluationContext ctx = support.evaluationForWrite(evaluationId, user);
        if (ctx.evaluation().getStatus() == EvaluationStatus.CONFIRMED) {
            throw ApiException.conflict("EVALUATION_CONFIRMED",
                    "The evaluation is confirmed and read-only; changes go through a dispute");
        }
        if (expectedVersion != null && !expectedVersion.equals(ctx.evaluation().getVersion())) {
            throw versionConflict();
        }
        return ctx;
    }

    private QuestionGrade requireGrade(GradeEvaluation evaluation, UUID gradeId) {
        return grades.findById(gradeId)
                .filter(g -> g.getEvaluationId().equals(evaluation.getEvaluationId()))
                .orElseThrow(() -> ApiException.notFound("QUESTION_GRADE_NOT_FOUND", "Thread grade not found"));
    }

    private void copyAi(GradeEvaluation evaluation, QuestionGrade grade, List<CriterionScore> criteria, User user,
                        Instant now) {
        for (CriterionScore c : criteria) {
            if (c.getAiScore() != null) {
                setFinal(evaluation, grade, c, c.getAiScore(), user, now);
            }
        }
        recompute(grade, criteria, user, now);
    }

    private void setFinal(GradeEvaluation evaluation, QuestionGrade grade, CriterionScore score, BigDecimal value,
                          User user, Instant now) {
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        if (score.getFinalScore() != null && score.getFinalScore().compareTo(scaled) == 0) {
            return;
        }
        log(evaluation, grade, score.getCriterionScoreId(), "final_score", plain(score.getFinalScore()), plain(scaled),
                user, now);
        score.setFinalScore(scaled);
    }

    /** Thread final score once every criterion has a final; the thread is then CONFIRMED (06 §4 step 7). */
    private static void recompute(QuestionGrade grade, List<CriterionScore> criteria, User user, Instant now) {
        BigDecimal finalScore = ScoreCalculator.questionScore(EvaluationFactory.parts(criteria));
        grade.setFinalScore(finalScore);
        if (finalScore != null && grade.getStatus() != QuestionGradeStatus.CONFIRMED) {
            grade.setStatus(QuestionGradeStatus.CONFIRMED);
            grade.setConfirmedBy(user.getUserId());
            grade.setConfirmedAt(now);
        }
    }

    private void log(GradeEvaluation evaluation, QuestionGrade grade, UUID criterionScoreId, String field,
                     String oldValue, String newValue, User user, Instant now) {
        GradeChangeLog row = new GradeChangeLog();
        row.setChangeId(UUID.randomUUID());
        row.setEvaluationId(evaluation.getEvaluationId());
        row.setQuestionGradeId(grade == null ? null : grade.getQuestionGradeId());
        row.setCriterionScoreId(criterionScoreId);
        row.setField(field);
        row.setOldValue(oldValue);
        row.setNewValue(newValue);
        row.setChangedBy(user.getUserId());
        row.setChangedAt(now);
        changeLog.save(row);
    }

    /** Touches the evaluation so its version increases on every write, flushes, and maps the result. */
    private EvaluationDto save(GradeEvaluation evaluation, SessionInfo session, Instant now) {
        touch(evaluation, now);
        try {
            evaluations.saveAndFlush(evaluation);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionConflict();
        }
        return toDto(evaluation, session);
    }

    static void touch(GradeEvaluation evaluation, Instant now) {
        Instant previous = evaluation.getUpdatedAt();
        evaluation.setUpdatedAt(previous != null && !now.isAfter(previous) ? previous.plusNanos(1_000) : now);
    }

    private static ApiException versionConflict() {
        return ApiException.conflict("VERSION_CONFLICT", "The evaluation was changed by someone else; reload and retry");
    }

    private Map<UUID, List<CriterionScore>> criteriaOf(List<QuestionGrade> all) {
        if (all.isEmpty()) {
            return Map.of();
        }
        return criterionScores.findByQuestionGradeIdIn(all.stream().map(QuestionGrade::getQuestionGradeId).toList())
                .stream().collect(Collectors.groupingBy(CriterionScore::getQuestionGradeId));
    }

    private Map<UUID, Integer> orderNumbers(UUID sessionId) {
        Map<UUID, Integer> result = new HashMap<>();
        interviewApi.getThreads(sessionId).forEach(t -> result.put(t.sessionQuestionId(), t.orderNo()));
        return result;
    }

    private EvaluationDto toDto(GradeEvaluation evaluation, SessionInfo session) {
        List<QuestionGrade> all = grades.findByEvaluationId(evaluation.getEvaluationId());
        Map<UUID, List<CriterionScore>> criteria = criteriaOf(all);
        Map<UUID, ThreadInfo> threads = interviewApi.getThreads(session.sessionId()).stream()
                .collect(Collectors.toMap(ThreadInfo::sessionQuestionId, Function.identity()));
        GradingSupport.Student student = support.student(session.studentId());

        List<ThreadDto> threadDtos = new ArrayList<>();
        for (QuestionGrade g : all) {
            ThreadInfo thread = threads.get(g.getSessionQuestionId());
            GradingJson.Snapshot snapshot = GradingJson.snapshot(g.getRubricSnapshot());
            Map<UUID, CriterionScore> scores = criteria.getOrDefault(g.getQuestionGradeId(), List.of()).stream()
                    .collect(Collectors.toMap(CriterionScore::getCriterionId, Function.identity()));
            List<CriterionDto> criterionDtos = snapshot.criteria().stream()
                    .sorted(Comparator.comparingInt(GradingJson.SnapshotCriterion::sortOrder))
                    .filter(c -> scores.containsKey(c.criterionId()))
                    .map(c -> {
                        CriterionScore s = scores.get(c.criterionId());
                        return new CriterionDto(s.getCriterionScoreId(), c.criterionId(), c.name(), c.description(),
                                s.getMaxScore(), s.getWeightPercent(), s.getAiScore(), s.getAiJustification(),
                                s.getFinalScore());
                    }).toList();
            QuestionRef question = questionBankApi.getQuestion(g.getQuestionId())
                    .map(q -> new QuestionRef(q.questionId(), q.content(), q.referenceAnswer(), q.bloomLevel()))
                    .orElse(new QuestionRef(g.getQuestionId(), null, null, null));
            List<TurnDto> turns = thread == null ? List.of() : thread.turns().stream()
                    .map(t -> new TurnDto(t.turnId(), t.turnType(), t.followupIndex(), t.questionText(), t.transcript(),
                            t.status(), t.answerAudioKey() == null ? null : "/api/v1/turns/" + t.turnId() + "/answer-audio",
                            t.responseDurationSec(), t.askedAt(), t.followupDecision()))
                    .toList();
            threadDtos.add(new ThreadDto(g.getQuestionGradeId(), g.getSessionQuestionId(),
                    thread == null ? 0 : thread.orderNo(), g.getStatus().name(), g.isIncludeInTotal(), question,
                    new RubricRef(snapshot.rubricId(), snapshot.name()), turns, criterionDtos, g.getAiScore(),
                    g.getFinalScore(), GradingJson.strings(g.getAiStrengths()), GradingJson.strings(g.getAiWeaknesses()),
                    GradingJson.strings(g.getAiMissingPoints()), g.getAiFeedback(), g.getAiError(),
                    GradingJson.signals(g.getSignals()), g.getLecturerComment(), g.getConfirmedBy(), g.getConfirmedAt()));
        }
        threadDtos.sort(Comparator.comparingInt(ThreadDto::orderNo));
        List<QuestionGrade> included = all.stream().filter(QuestionGrade::isIncludeInTotal).toList();
        BigDecimal current = included.stream().anyMatch(g -> g.getFinalScore() == null) ? null
                : ScoreCalculator.mean(included.stream().map(QuestionGrade::getFinalScore).toList());
        return new EvaluationDto(evaluation.getEvaluationId(), session.sessionId(), session.vivaExamId(),
                evaluation.getStatus().name(), evaluation.getVersion(),
                new StudentRef(student.id(), student.fullName(), student.studentCode()), evaluation.getAiTotalScore(),
                evaluation.getFinalTotalScore(), current, evaluation.getAiGeneralFeedback(),
                evaluation.getLecturerComment(), evaluation.getConfirmedBy(), evaluation.getConfirmedAt(),
                evaluation.getCreatedAt(), evaluation.getUpdatedAt(), threadDtos);
    }

    static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    static <T> PageResponse<T> page(List<T> all, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 500));
        int safePage = Math.max(0, page);
        int from = Math.min(all.size(), safePage * safeSize);
        int to = Math.min(all.size(), from + safeSize);
        return new PageResponse<>(all.subList(from, to), safePage, safeSize, all.size());
    }
}
