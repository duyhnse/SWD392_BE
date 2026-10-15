package swd392.group6.AIVES.grading.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamApi;
import swd392.group6.AIVES.exam.ExamApi.ExamInfo;
import swd392.group6.AIVES.exam.ExamApi.AttemptInfo;
import swd392.group6.AIVES.grading.internal.GradingDtos.ResultCriterionDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.ResultDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.ResultSummaryDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.ResultThreadDto;
import swd392.group6.AIVES.interview.InterviewApi;
import swd392.group6.AIVES.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FG6 student results: only the student's own evaluations that are CONFIRMED and whose buổi thi has released
 * results. Never shows reference answers, AI scores or other students (15 §6).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class StudentResultService {

    /** Phúc khảo may be filed within this period after the results are released (15 §5.5). */
    static final Duration DISPUTE_WINDOW = Duration.ofDays(7);

    private final GradingSupport support;
    private final ExamApi examApi;
    private final GradeEvaluationRepository evaluations;
    private final QuestionGradeRepository grades;
    private final CriterionScoreRepository criterionScores;
    private final GradeDisputeRepository disputes;
    private final InterviewApi interviewApi;
    private final Clock clock;

    /** A released result of the caller: evaluation, attempt and exam. */
    record Visible(GradeEvaluation evaluation, AttemptInfo attempt, ExamInfo exam) {
    }

    public List<ResultSummaryDto> list(User student) {
        Map<UUID, UUID> attemptToExam = support.attemptExamIdsOfStudent(student.getUserId());
        if (attemptToExam.isEmpty()) {
            return List.of();
        }
        Map<UUID, ExamInfo> exams = new HashMap<>();
        return evaluations.findByAttemptIdIn(attemptToExam.keySet()).stream()
                .filter(e -> e.getStatus() == EvaluationStatus.CONFIRMED)
                .map(e -> {
                    ExamInfo exam = exams.computeIfAbsent(attemptToExam.get(e.getAttemptId()),
                            id -> examApi.getExam(id).orElse(null));
                    if (exam == null || !exam.resultsReleased()) {
                        return null;
                    }
                    return new ResultSummaryDto(e.getEvaluationId(), e.getAttemptId(), exam.vivaExamId(), exam.title(),
                            e.getFinalTotalScore(), e.getConfirmedAt(), exam.resultsReleasedAt());
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ResultSummaryDto::confirmedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public ResultDto get(UUID evaluationId, User student) {
        Visible visible = requireVisible(evaluationId, student);
        GradeEvaluation evaluation = visible.evaluation();
        List<QuestionGrade> all = grades.findByEvaluationId(evaluationId);
        Map<UUID, List<CriterionScore>> criteria = all.isEmpty() ? Map.of()
                : criterionScores.findByQuestionGradeIdIn(all.stream().map(QuestionGrade::getQuestionGradeId).toList())
                .stream().collect(Collectors.groupingBy(CriterionScore::getQuestionGradeId));
        Map<UUID, Integer> orderNo = new HashMap<>();
        Map<UUID, String> contents = new HashMap<>();
        examApi.getAttemptQuestions(visible.attempt().attemptId()).forEach(q -> {
            orderNo.put(q.attemptQuestionId(), q.orderNo());
            contents.put(q.attemptQuestionId(), q.content());
        });

        List<ResultThreadDto> threads = all.stream().map(g -> {
            GradingJson.Snapshot snapshot = GradingJson.snapshot(g.getRubricSnapshot());
            Map<UUID, CriterionScore> scores = criteria.getOrDefault(g.getQuestionGradeId(), List.of()).stream()
                    .collect(Collectors.toMap(CriterionScore::getCriterionId, Function.identity()));
            List<ResultCriterionDto> criterionDtos = snapshot.criteria().stream()
                    .sorted(Comparator.comparingInt(GradingJson.SnapshotCriterion::sortOrder))
                    .filter(c -> scores.containsKey(c.criterionId()))
                    .map(c -> new ResultCriterionDto(c.name(), c.maxScore(), c.weightPercent(),
                            scores.get(c.criterionId()).getFinalScore()))
                    .toList();
            String content = contents.get(g.getAttemptQuestionId());
            return new ResultThreadDto(g.getQuestionGradeId(), orderNo.getOrDefault(g.getAttemptQuestionId(), 0), content,
                    g.isIncludeInTotal(), g.getFinalScore(), criterionDtos, GradingJson.strings(g.getAiStrengths()),
                    GradingJson.strings(g.getAiWeaknesses()), GradingJson.strings(g.getAiMissingPoints()),
                    g.getAiFeedback(), g.getLecturerComment());
        }).sorted(Comparator.comparingInt(ResultThreadDto::orderNo)).toList();

        Instant deadline = disputeDeadline(visible.exam());
        boolean canDispute = deadline != null && !clock.instant().isAfter(deadline)
                && !disputes.existsByEvaluationIdAndStatus(evaluationId, DisputeStatus.OPEN);
        return new ResultDto(evaluationId, visible.attempt().attemptId(), visible.exam().vivaExamId(),
                visible.exam().title(), evaluation.getFinalTotalScore(), evaluation.getLecturerComment(),
                evaluation.getConfirmedAt(), visible.exam().resultsReleasedAt(), deadline, canDispute, threads);
    }

    /** The caller's own evaluation, CONFIRMED and released; otherwise 404 RESULT_NOT_FOUND. */
    public Visible requireVisible(UUID evaluationId, User student) {
        GradeEvaluation evaluation = evaluations.findById(evaluationId).orElseThrow(StudentResultService::notFound);
        AttemptInfo attempt = examApi.getAttempt(evaluation.getAttemptId()).orElseThrow(StudentResultService::notFound);
        if (!attempt.studentId().equals(student.getUserId()) || evaluation.getStatus() != EvaluationStatus.CONFIRMED) {
            throw notFound();
        }
        ExamInfo exam = examApi.getExam(attempt.vivaExamId()).orElseThrow(StudentResultService::notFound);
        if (!exam.resultsReleased()) {
            throw notFound();
        }
        return new Visible(evaluation, attempt, exam);
    }

    static Instant disputeDeadline(ExamInfo exam) {
        return exam.resultsReleasedAt() == null ? null : exam.resultsReleasedAt().plus(DISPUTE_WINDOW);
    }

    private static ApiException notFound() {
        return ApiException.notFound("RESULT_NOT_FOUND", "Result not found or not released yet");
    }
}
