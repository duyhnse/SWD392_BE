package swd392.group6.AIVES.grading.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import swd392.group6.AIVES.ai.AiJobFinishedEvent;
import swd392.group6.AIVES.ai.AiJobService;
import swd392.group6.AIVES.ai.AiJobType;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamApi;
import swd392.group6.AIVES.exam.ExamApi.AttemptInfo;
import swd392.group6.AIVES.exam.ExamApi.AttemptQuestionInfo;
import swd392.group6.AIVES.grading.GradingApi;
import swd392.group6.AIVES.interview.AttemptCompletedEvent;
import swd392.group6.AIVES.interview.InterviewApi;
import swd392.group6.AIVES.interview.InterviewApi.ThreadInfo;
import swd392.group6.AIVES.user.NotificationApi;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * WF4 AI grading (06 §4): a completed attempt gets its evaluation, one {@code GRADE_THREAD} job per thread that needs AI
 * (P-GRADE on the AI node), the answers are validated (06 §6) into {@code ai_*} fields only (BR-G1), and once no thread
 * is pending the evaluation is {@code AWAITING_REVIEW}, summarised ({@code SUMMARIZE_EVALUATION}) and the examiner is
 * told. Nothing here ever writes a final score.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiGradingPipeline {

    static final String THREAD_SUBJECT = "QUESTION_GRADE";
    static final String SUMMARY_SUBJECT = "GRADE_EVALUATION";
    static final int MAX_JUSTIFICATION = 600;
    static final int MAX_LIST_ITEMS = 6;
    static final int MAX_ITEM = 200;
    /** EX-G5: a thread pending this long without an open job is queued again. */
    static final Duration STUCK_AFTER = Duration.ofMinutes(5);

    private final GradingApi gradingApi;
    private final ExamApi examApi;
    private final InterviewApi interviewApi;
    private final AiJobService aiJobs;
    private final GradeEvaluationRepository evaluations;
    private final QuestionGradeRepository grades;
    private final CriterionScoreRepository criterionScores;
    private final NotificationApi notifications;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    // ---------------------------------------------------------------------------------------------- start

    /** PRE-G1: after the attempt is committed as COMPLETED. */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AttemptCompletedEvent event) {
        try {
            GradingApi.CreatedEvaluation created = gradingApi.createEvaluation(event.attemptId(), GradingApi.Mode.AI);
            if (created.created()) {
                start(created.evaluationId());
            }
        } catch (ApiException e) { // e.g. RUBRIC_MISSING: the lecturer grades by hand
            log.warn("No AI grading for attempt {}: {} {}", event.attemptId(), e.getCode(), e.getMessage());
        }
    }

    /** Queues every PENDING_AI thread of an evaluation; finishes at once when none is. Must run in a transaction. */
    void start(UUID evaluationId) {
        GradeEvaluation evaluation = evaluations.findById(evaluationId).orElseThrow();
        AttemptInfo attempt = examApi.getAttempt(evaluation.getAttemptId()).orElseThrow();
        Map<UUID, AttemptQuestionInfo> questions = new HashMap<>();
        examApi.getAttemptQuestions(attempt.attemptId()).forEach(q -> questions.put(q.attemptQuestionId(), q));
        Map<UUID, ThreadInfo> threads = new HashMap<>();
        interviewApi.getThreads(attempt.attemptId()).forEach(t -> threads.put(t.attemptQuestionId(), t));
        boolean pending = false;
        for (QuestionGrade grade : grades.findByEvaluationId(evaluationId)) {
            if (grade.getStatus() == QuestionGradeStatus.PENDING_AI) {
                aiJobs.enqueue(AiJobType.GRADE_THREAD, THREAD_SUBJECT, grade.getQuestionGradeId(),
                        input(grade, questions.get(grade.getAttemptQuestionId()), threads.get(grade.getAttemptQuestionId())));
                pending = true;
            }
        }
        if (!pending) {
            finish(evaluation, attempt);
        }
    }

    /** Contract 16 §4.1 {@code GRADE_THREAD} input. Signals are not sent: they never feed the AI score (BR-G5). */
    static Map<String, Object> input(QuestionGrade grade, AttemptQuestionInfo q, ThreadInfo thread) {
        GradingJson.Snapshot rubric = GradingJson.snapshot(grade.getRubricSnapshot());
        List<Map<String, Object>> criteria = new ArrayList<>();
        for (GradingJson.SnapshotCriterion c : rubric.criteria()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("criterion_id", c.criterionId().toString());
            row.put("name", c.name());
            row.put("description", c.description() == null ? "" : c.description());
            row.put("max_score", c.maxScore());
            row.put("weight_percent", c.weightPercent());
            criteria.add(row);
        }
        List<Map<String, Object>> turns = new ArrayList<>();
        for (InterviewApi.TurnInfo t : thread == null ? List.<InterviewApi.TurnInfo>of() : thread.turns()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", t.turnType());
            row.put("question_text", t.questionText() == null ? "" : t.questionText());
            row.put("transcript", t.transcript() == null ? "" : t.transcript());
            turns.add(row);
        }
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("content", q.content());
        question.put("bloom_level", q.bloomLevel());
        question.put("reference_answer", q.referenceAnswer() == null ? "" : q.referenceAnswer());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("language", q.language());
        input.put("question", question);
        input.put("rubric", Map.of("name", rubric.name() == null ? "" : rubric.name(), "criteria", criteria));
        input.put("turns", turns);
        return input;
    }

    // ---------------------------------------------------------------------------------------------- results

    /** REQUIRES_NEW: a job can finish inside the caller's afterCommit, where the old transaction is still bound. */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFinished(AiJobFinishedEvent event) {
        if (event.type() == AiJobType.GRADE_THREAD && THREAD_SUBJECT.equals(event.subjectType())) {
            aiJobs.find(event.jobId()).ifPresent(job -> graded(event.subjectId(), job));
        } else if (event.type() == AiJobType.SUMMARIZE_EVALUATION && SUMMARY_SUBJECT.equals(event.subjectType())) {
            aiJobs.find(event.jobId()).ifPresent(job -> summarised(event.subjectId(), job));
        }
    }

    private void graded(UUID gradeId, AiJobService.AiJob job) {
        Optional<QuestionGrade> found = grades.findById(gradeId);
        if (found.isEmpty() || found.get().getStatus() != QuestionGradeStatus.PENDING_AI) {
            return; // retried or graded by hand meanwhile: the first answer that matters wins
        }
        QuestionGrade grade = found.get();
        GradeEvaluation evaluation = evaluations.findById(grade.getEvaluationId()).orElseThrow();
        if (evaluation.getStatus() == EvaluationStatus.CONFIRMED) {
            return;
        }
        List<CriterionScore> criteria = criterionScores.findByQuestionGradeIdIn(List.of(gradeId));
        String problem = !"SUCCEEDED".equals(job.status())
                ? (job.errorCode() != null ? job.errorCode() : job.status()) + (job.errorMessage() != null ? ": " + job.errorMessage() : "")
                : apply(grade, criteria, job.result());
        if (problem != null) {
            grade.setStatus(QuestionGradeStatus.AI_FAILED);
            grade.setAiError(problem.length() > 1000 ? problem.substring(0, 1000) : problem);
        } else {
            grade.setStatus(QuestionGradeStatus.AI_SCORED);
            grade.setAiError(null);
            if (job.result().hasNonNull("model")) {
                evaluation.setLlmModel(trim(job.result().path("model").asString(), 100));
            }
        }
        grades.saveAndFlush(grade);
        if (grades.findByEvaluationId(evaluation.getEvaluationId()).stream()
                .noneMatch(g -> g.getStatus() == QuestionGradeStatus.PENDING_AI)) {
            finish(evaluation, examApi.getAttempt(evaluation.getAttemptId()).orElseThrow());
        } else {
            refreshAiTotal(evaluation);
        }
    }

    /**
     * 06 §6: one entry per criterion of the snapshot, scores 0..max (rounded to 0.25), a justification each, short lists.
     * Writes only {@code ai_*} fields. @return null when valid, else why not (nothing is written then).
     */
    static String apply(QuestionGrade grade, List<CriterionScore> criteria, JsonNode result) {
        if (result == null || !result.path("criteria").isArray()) {
            return "INVALID_RESULT: no criteria";
        }
        Map<String, JsonNode> given = new HashMap<>();
        result.path("criteria").forEach(c -> given.put(c.path("criterion_id").asString(""), c));
        if (given.size() != criteria.size()
                || criteria.stream().anyMatch(c -> !given.containsKey(c.getCriterionId().toString()))) {
            return "INVALID_RESULT: criteria do not match the rubric";
        }
        Map<UUID, BigDecimal> scores = new HashMap<>();
        for (CriterionScore c : criteria) {
            JsonNode g = given.get(c.getCriterionId().toString());
            if (!g.path("score").isNumber()) {
                return "INVALID_RESULT: score missing";
            }
            BigDecimal score = ScoreCalculator.roundToQuarter(g.path("score").decimalValue());
            if (score.signum() < 0 || score.compareTo(c.getMaxScore()) > 0 || g.path("justification").asString("").isBlank()) {
                return "INVALID_RESULT: score out of range or no justification";
            }
            scores.put(c.getCriterionId(), score);
        }
        for (CriterionScore c : criteria) {
            c.setAiScore(scores.get(c.getCriterionId()));
            c.setAiJustification(trim(given.get(c.getCriterionId().toString()).path("justification").asString(), MAX_JUSTIFICATION));
        }
        grade.setAiStrengths(list(result.path("strengths")));
        grade.setAiWeaknesses(list(result.path("weaknesses")));
        grade.setAiMissingPoints(list(result.path("missing_points")));
        grade.setAiFeedback(trim(result.path("feedback").asString(""), 2000));
        grade.setAiScore(ScoreCalculator.questionScore(EvaluationFactory.aiParts(criteria)));
        return null;
    }

    private static String list(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(n -> {
                if (out.size() < MAX_LIST_ITEMS && !n.asString("").isBlank()) {
                    out.add(trim(n.asString().strip(), MAX_ITEM));
                }
            });
        }
        return GradingJson.write(out);
    }

    /** No thread pending: AWAITING_REVIEW (once), AI total, optional summary, examiner notified. */
    private void finish(GradeEvaluation evaluation, AttemptInfo attempt) {
        refreshAiTotal(evaluation);
        if (evaluation.getStatus() != EvaluationStatus.PENDING_AI) {
            return; // a retried thread finished on an evaluation already under review
        }
        evaluation.setStatus(EvaluationStatus.AWAITING_REVIEW);
        evaluation.setUpdatedAt(clock.instant());
        evaluations.saveAndFlush(evaluation);
        List<Map<String, Object>> rows = summaryRows(evaluation);
        if (!rows.isEmpty()) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("language", language(attempt));
            input.put("threads", rows);
            aiJobs.enqueue(AiJobType.SUMMARIZE_EVALUATION, SUMMARY_SUBJECT, evaluation.getEvaluationId(), input);
        }
        if (attempt.examinerId() != null) {
            notifications.notify(attempt.examinerId(), "GRADING_READY", "AI đã chấm xong một lượt thi",
                    "Mở Chấm điểm để xem gợi ý của AI và chốt điểm.",
                    Map.of("evaluationId", evaluation.getEvaluationId().toString(), "attemptId", attempt.attemptId().toString(),
                            "vivaExamId", attempt.vivaExamId().toString()));
        }
    }

    private void refreshAiTotal(GradeEvaluation evaluation) {
        List<BigDecimal> scores = grades.findByEvaluationId(evaluation.getEvaluationId()).stream()
                .filter(g -> g.isIncludeInTotal() && g.getAiScore() != null).map(QuestionGrade::getAiScore).toList();
        evaluation.setAiTotalScore(ScoreCalculator.mean(scores));
        evaluation.setUpdatedAt(clock.instant());
        evaluations.saveAndFlush(evaluation);
    }

    private List<Map<String, Object>> summaryRows(GradeEvaluation evaluation) {
        Map<UUID, AttemptQuestionInfo> questions = new HashMap<>();
        examApi.getAttemptQuestions(evaluation.getAttemptId()).forEach(q -> questions.put(q.attemptQuestionId(), q));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QuestionGrade g : grades.findByEvaluationId(evaluation.getEvaluationId())) {
            AttemptQuestionInfo q = questions.get(g.getAttemptQuestionId());
            if (g.getStatus() != QuestionGradeStatus.AI_SCORED || q == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bloom_level", q.bloomLevel());
            row.put("question", q.content());
            row.put("ai_score", g.getAiScore());
            row.put("strengths", GradingJson.strings(g.getAiStrengths()));
            row.put("weaknesses", GradingJson.strings(g.getAiWeaknesses()));
            row.put("missing_points", GradingJson.strings(g.getAiMissingPoints()));
            rows.add(row);
        }
        return rows;
    }

    private String language(AttemptInfo attempt) {
        return examApi.getAttemptQuestions(attempt.attemptId()).stream().map(AttemptQuestionInfo::language)
                .filter(l -> l != null).findFirst().orElse("VI");
    }

    private void summarised(UUID evaluationId, AiJobService.AiJob job) {
        evaluations.findById(evaluationId).ifPresent(e -> {
            if ("SUCCEEDED".equals(job.status()) && e.getStatus() != EvaluationStatus.CONFIRMED
                    && !job.result().path("summary").asString("").isBlank()) {
                e.setAiGeneralFeedback(trim(job.result().path("summary").asString().strip(), 2000));
                e.setUpdatedAt(clock.instant());
                evaluations.saveAndFlush(e);
            }
        });
    }

    // ---------------------------------------------------------------------------------------------- retry & sweep

    /** EX-G2 "Retry AI": the thread goes back to PENDING_AI and a new job is queued. Must run in a transaction. */
    void retry(QuestionGrade grade, GradeEvaluation evaluation) {
        AttemptQuestionInfo q = examApi.getAttemptQuestions(evaluation.getAttemptId()).stream()
                .filter(x -> x.attemptQuestionId().equals(grade.getAttemptQuestionId())).findFirst().orElseThrow();
        ThreadInfo thread = interviewApi.getThreads(evaluation.getAttemptId()).stream()
                .filter(t -> t.attemptQuestionId().equals(grade.getAttemptQuestionId())).findFirst().orElse(null);
        if (thread == null || thread.turns().stream().allMatch(t -> t.transcript() == null || t.transcript().isBlank())) {
            throw ApiException.conflict("NO_TRANSCRIPT", "This thread has no transcript the AI could grade");
        }
        grade.setStatus(QuestionGradeStatus.PENDING_AI);
        grade.setAiError(null);
        grades.saveAndFlush(grade);
        aiJobs.enqueue(AiJobType.GRADE_THREAD, THREAD_SUBJECT, grade.getQuestionGradeId(), input(grade, q, thread));
    }

    /** EX-G5: threads still PENDING_AI after 5 min with no open job (e.g. a restart before enqueue) are queued again. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void sweep() {
        Instant before = clock.instant().minus(STUCK_AFTER);
        List<UUID> stuck = jdbc.queryForList("""
                        select g.question_grade_id from question_grades g
                        join grade_evaluations e on e.evaluation_id = g.evaluation_id
                        where g.status = 'PENDING_AI' and e.updated_at < ?
                          and not exists (select 1 from ai_jobs j where j.subject_type = ? and j.subject_id = g.question_grade_id
                                          and j.status in ('QUEUED', 'SUBMITTED'))""", UUID.class,
                java.sql.Timestamp.from(before), THREAD_SUBJECT);
        for (UUID id : stuck) {
            grades.findById(id).ifPresent(g -> {
                GradeEvaluation e = evaluations.findById(g.getEvaluationId()).orElseThrow();
                try {
                    retry(g, e);
                } catch (ApiException ex) {
                    g.setStatus(QuestionGradeStatus.MISSING_DATA);
                    grades.saveAndFlush(g);
                }
            });
        }
        if (!stuck.isEmpty()) {
            log.info("AI grading sweep: {} thread(s) queued again", stuck.size());
        }
    }

    private static String trim(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
