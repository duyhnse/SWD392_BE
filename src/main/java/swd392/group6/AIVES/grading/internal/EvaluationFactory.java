package swd392.group6.AIVES.grading.internal;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamApi;
import swd392.group6.AIVES.exam.ExamApi.AttemptInfo;
import swd392.group6.AIVES.grading.GradingApi;
import swd392.group6.AIVES.interview.InterviewApi;
import swd392.group6.AIVES.interview.InterviewApi.ThreadInfo;
import swd392.group6.AIVES.interview.InterviewApi.TurnInfo;
import swd392.group6.AIVES.questionbank.QuestionBankApi.RubricSnapshot;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates evaluations (06 §4 steps 1–2 and the thread classification). Shared by the lecturer endpoint and the
 * future AI pipeline.
 *
 * <p>Thread classification:
 * <ul>
 *   <li>attempt question {@code SKIPPED} → {@code NOT_ASKED}, not included in the total;</li>
 *   <li>{@code NOT_REACHED} → {@code NOT_ASKED}, included, final scores pre-filled with 0;</li>
 *   <li>turns that are all {@code NO_ANSWER} → {@code AI_SCORED} by the system with every AI score 0;</li>
 *   <li>no non-blank transcript → {@code MISSING_DATA} (manual grading);</li>
 *   <li>otherwise {@code PENDING_AI} (mode AI) or {@code AI_FAILED} with {@code ai_error = AI_NOT_REQUESTED} (mode MANUAL).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
class EvaluationFactory implements GradingApi {

    static final String AI_NOT_REQUESTED = "AI_NOT_REQUESTED";
    static final String NOT_REACHED_COMMENT = "Not reached before time limit";
    static final String NO_ANSWER_FEEDBACK = "No answer";

    private final ExamApi examApi;
    private final InterviewApi interviewApi;
    private final GradeEvaluationRepository evaluations;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    @Transactional
    public CreatedEvaluation createEvaluation(UUID attemptId, Mode mode) {
        AttemptInfo attempt = examApi.getAttempt(attemptId)
                .orElseThrow(() -> ApiException.notFound("ATTEMPT_NOT_FOUND", "Attempt not found"));
        Optional<GradeEvaluation> existing = evaluations.findByAttemptId(attemptId);
        if (existing.isPresent()) {
            return new CreatedEvaluation(existing.get().getEvaluationId(), existing.get().getStatus().name(), false);
        }
        if (!"COMPLETED".equals(attempt.status())) {
            throw ApiException.conflict("ATTEMPT_NOT_COMPLETED", "Only a completed attempt can be graded");
        }

        // Grade with the rubric copied at check-in (D49), never the current bank version; replaced questions are not graded.
        List<ThreadInfo> threads = interviewApi.getThreads(attemptId).stream()
                .filter(t -> !"VOIDED".equals(t.status())).toList();
        Map<UUID, RubricSnapshot> snapshots = new HashMap<>();
        examApi.getAttemptQuestions(attemptId).forEach(q -> snapshots.put(q.attemptQuestionId(),
                GradingJson.readRubric(q.rubricSnapshotJson())));
        Map<UUID, RubricSnapshot> rubrics = new HashMap<>();
        List<Integer> withoutRubric = new ArrayList<>();
        for (ThreadInfo thread : threads) {
            RubricSnapshot rubric = snapshots.get(thread.attemptQuestionId());
            if (rubric == null || rubric.criteria() == null || rubric.criteria().isEmpty()) {
                withoutRubric.add(thread.orderNo());
            } else {
                rubrics.put(thread.attemptQuestionId(), rubric);
            }
        }
        if (!withoutRubric.isEmpty()) {
            throw ApiException.unprocessable("RUBRIC_MISSING", "Questions without a rubric: " + withoutRubric);
        }

        Instant now = clock.instant();
        GradeEvaluation evaluation = new GradeEvaluation();
        evaluation.setEvaluationId(UUID.randomUUID());
        evaluation.setAttemptId(attemptId);
        evaluation.setCreatedAt(now);
        evaluation.setUpdatedAt(now);

        boolean anyPendingAi = false;
        List<BigDecimal> aiScores = new ArrayList<>();
        List<Object> rows = new ArrayList<>();
        for (ThreadInfo thread : threads) {
            RubricSnapshot rubric = rubrics.get(thread.attemptQuestionId());
            QuestionGrade grade = new QuestionGrade();
            grade.setQuestionGradeId(UUID.randomUUID());
            grade.setEvaluationId(evaluation.getEvaluationId());
            grade.setAttemptQuestionId(thread.attemptQuestionId());
            grade.setQuestionId(thread.questionId());
            grade.setRubricSnapshot(GradingJson.write(GradingJson.Snapshot.of(rubric)));
            grade.setSignals(GradingJson.write(signals(thread.turns())));

            Classification kind = classify(thread);
            List<CriterionScore> criteria = new ArrayList<>();
            for (RubricSnapshot.Criterion c : rubric.criteria()) {
                CriterionScore score = new CriterionScore();
                score.setCriterionScoreId(UUID.randomUUID());
                score.setQuestionGradeId(grade.getQuestionGradeId());
                score.setCriterionId(c.criterionId());
                score.setMaxScore(c.maxScore());
                score.setWeightPercent(c.weightPercent());
                if (kind == Classification.NOT_REACHED) {
                    score.setFinalScore(BigDecimal.ZERO);
                } else if (kind == Classification.NO_ANSWER) {
                    score.setAiScore(BigDecimal.ZERO);
                    score.setAiJustification(NO_ANSWER_FEEDBACK);
                }
                criteria.add(score);
            }
            switch (kind) {
                case SKIPPED -> {
                    grade.setStatus(QuestionGradeStatus.NOT_ASKED);
                    grade.setIncludeInTotal(false);
                }
                case NOT_REACHED -> {
                    grade.setStatus(QuestionGradeStatus.NOT_ASKED);
                    grade.setFinalScore(ScoreCalculator.questionScore(parts(criteria)));
                    grade.setLecturerComment(NOT_REACHED_COMMENT);
                }
                case NO_ANSWER -> {
                    grade.setStatus(QuestionGradeStatus.AI_SCORED);
                    grade.setAiScore(ScoreCalculator.questionScore(aiParts(criteria)));
                    grade.setAiFeedback(NO_ANSWER_FEEDBACK);
                    grade.setAiStrengths("[]");
                    grade.setAiWeaknesses("[]");
                    grade.setAiMissingPoints("[]");
                }
                case MISSING_DATA -> grade.setStatus(QuestionGradeStatus.MISSING_DATA);
                case NEEDS_AI -> {
                    if (mode == Mode.AI) {
                        grade.setStatus(QuestionGradeStatus.PENDING_AI);
                        anyPendingAi = true;
                    } else {
                        grade.setStatus(QuestionGradeStatus.AI_FAILED);
                        grade.setAiError(AI_NOT_REQUESTED);
                    }
                }
            }
            if (grade.isIncludeInTotal() && grade.getAiScore() != null) {
                aiScores.add(grade.getAiScore());
            }
            rows.add(grade);
            rows.addAll(criteria);
        }
        evaluation.setStatus(anyPendingAi ? EvaluationStatus.PENDING_AI : EvaluationStatus.AWAITING_REVIEW);
        evaluation.setAiTotalScore(ScoreCalculator.mean(aiScores));
        entityManager.persist(evaluation);
        rows.forEach(entityManager::persist);
        entityManager.flush();
        return new CreatedEvaluation(evaluation.getEvaluationId(), evaluation.getStatus().name(), true);
    }

    private enum Classification { SKIPPED, NOT_REACHED, NO_ANSWER, MISSING_DATA, NEEDS_AI }

    private static Classification classify(ThreadInfo thread) {
        if ("SKIPPED".equals(thread.status())) {
            return Classification.SKIPPED;
        }
        if ("NOT_REACHED".equals(thread.status())) {
            return Classification.NOT_REACHED;
        }
        List<TurnInfo> turns = thread.turns();
        if (!turns.isEmpty() && turns.stream().allMatch(t -> "NO_ANSWER".equals(t.status()))) {
            return Classification.NO_ANSWER;
        }
        boolean hasTranscript = turns.stream().anyMatch(t -> t.transcript() != null && !t.transcript().isBlank());
        return hasTranscript ? Classification.NEEDS_AI : Classification.MISSING_DATA;
    }

    static GradingJson.Signals signals(List<TurnInfo> turns) {
        int seconds = 0;
        int words = 0;
        int followups = 0;
        int noAnswer = 0;
        for (TurnInfo turn : turns) {
            if (turn.responseDurationSec() != null) {
                seconds += turn.responseDurationSec();
            }
            if (turn.transcript() != null && !turn.transcript().isBlank()) {
                words += turn.transcript().trim().split("\\s+").length;
            }
            if ("FOLLOW_UP".equals(turn.turnType())) {
                followups++;
            }
            if ("NO_ANSWER".equals(turn.status())) {
                noAnswer++;
            }
        }
        Integer wordsPerMin = seconds > 0 ? Math.round(words * 60f / seconds) : null;
        return new GradingJson.Signals(seconds, words, wordsPerMin, followups, noAnswer);
    }

    static List<ScoreCalculator.Part> parts(List<CriterionScore> criteria) {
        return criteria.stream()
                .map(c -> new ScoreCalculator.Part(c.getFinalScore(), c.getMaxScore(), c.getWeightPercent())).toList();
    }

    static List<ScoreCalculator.Part> aiParts(List<CriterionScore> criteria) {
        return criteria.stream()
                .map(c -> new ScoreCalculator.Part(c.getAiScore(), c.getMaxScore(), c.getWeightPercent())).toList();
    }
}
