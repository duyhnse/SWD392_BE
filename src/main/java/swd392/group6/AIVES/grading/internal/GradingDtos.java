package swd392.group6.AIVES.grading.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import swd392.group6.AIVES.questionbank.BloomLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response shapes of the grading endpoints (06 §9, 09 §4, 15 §5.5). */
final class GradingDtos {

    private GradingDtos() {
    }

    // ----- lecturer review --------------------------------------------------------------------------------------

    record StudentRef(UUID id, String fullName, String studentCode) {
    }

    record EvaluationDto(UUID id, UUID sessionId, UUID vivaExamId, String status, int version, StudentRef student,
                         BigDecimal aiTotalScore, BigDecimal finalTotalScore, BigDecimal currentTotalScore,
                         String aiGeneralFeedback, String lecturerComment, UUID confirmedBy, Instant confirmedAt,
                         Instant createdAt, Instant updatedAt, List<ThreadDto> threads) {
    }

    record QuestionRef(UUID id, String content, String referenceAnswer, BloomLevel bloomLevel) {
    }

    record RubricRef(UUID id, String name) {
    }

    record TurnDto(UUID turnId, String type, int followupIndex, String questionText, String transcript, String status,
                   String audioUrl, Integer durationSec, Instant askedAt, String decision) {
    }

    record CriterionDto(UUID criterionScoreId, UUID criterionId, String name, String description, BigDecimal maxScore,
                        BigDecimal weightPercent, BigDecimal aiScore, String aiJustification, BigDecimal finalScore) {
    }

    record ThreadDto(UUID gradeId, UUID sessionQuestionId, int orderNo, String status, boolean includeInTotal,
                     QuestionRef question, RubricRef rubric, List<TurnDto> turns, List<CriterionDto> criteria,
                     BigDecimal aiScore, BigDecimal finalScore, List<String> aiStrengths, List<String> aiWeaknesses,
                     List<String> aiMissingPoints, String aiFeedback, String aiError, GradingJson.Signals signals,
                     String lecturerComment, UUID confirmedBy, Instant confirmedAt) {
    }

    record EvaluationSummaryDto(UUID sessionId, StudentRef student, String sessionStatus, Instant endedAt,
                                UUID evaluationId, String evaluationStatus, BigDecimal aiTotalScore,
                                BigDecimal finalTotalScore, int aiFailedThreads, int missingDataThreads,
                                boolean openDispute) {
    }

    record ChangeDto(UUID id, UUID questionGradeId, Integer threadOrderNo, UUID criterionScoreId, String field,
                     String oldValue, String newValue, UUID changedBy, String changedByName, Instant changedAt) {
    }

    record CriterionFinal(@NotNull UUID criterionId, @NotNull BigDecimal finalScore) {
    }

    /** {@code lecturerComment} / {@code includeInTotal} left null are unchanged; an empty comment clears it. */
    record UpdateThreadRequest(@NotNull Integer version, @Valid List<CriterionFinal> criteria,
                               @Size(max = 4000) String lecturerComment, Boolean includeInTotal) {
    }

    record UpdateEvaluationRequest(@NotNull Integer version, @Size(max = 4000) String lecturerComment) {
    }

    /** Optional optimistic-lock check for the POST actions. */
    record VersionRequest(Integer version) {
    }

    record IncompleteThread(UUID gradeId, int orderNo) {
    }

    // ----- student results ----------------------------------------------------------------------------------------

    record ResultSummaryDto(UUID evaluationId, UUID sessionId, UUID vivaExamId, String examTitle,
                            BigDecimal finalTotalScore, Instant confirmedAt, Instant resultsReleasedAt) {
    }

    record ResultCriterionDto(String name, BigDecimal maxScore, BigDecimal weightPercent, BigDecimal finalScore) {
    }

    record ResultThreadDto(UUID gradeId, int orderNo, String questionContent, boolean includeInTotal,
                           BigDecimal finalScore, List<ResultCriterionDto> criteria, List<String> aiStrengths,
                           List<String> aiWeaknesses, List<String> aiMissingPoints, String aiFeedback,
                           String lecturerComment) {
    }

    record ResultDto(UUID evaluationId, UUID sessionId, UUID vivaExamId, String examTitle, BigDecimal finalTotalScore,
                     String lecturerComment, Instant confirmedAt, Instant resultsReleasedAt, Instant disputeDeadline,
                     boolean canDispute, List<ResultThreadDto> threads) {
    }

    // ----- disputes -------------------------------------------------------------------------------------------------

    record CreateDisputeRequest(@NotBlank @Size(max = 4000) String reason, List<UUID> questionGradeIds) {
    }

    record ResolutionRequest(@NotBlank @Size(max = 4000) String resolution) {
    }

    record DisputeDto(UUID id, UUID evaluationId, UUID sessionId, UUID vivaExamId, String examTitle, StudentRef student,
                      String reason, List<UUID> questionGradeIds, String status, String resolution, UUID resolvedBy,
                      Instant createdAt, Instant resolvedAt, String evaluationStatus) {
    }

    // ----- report ---------------------------------------------------------------------------------------------------

    record ScoreStats(int count, BigDecimal mean, BigDecimal median, BigDecimal min, BigDecimal max) {
    }

    record Bin(int from, int to, int count) {
    }

    record QuestionStats(UUID questionId, String content, int timesAsked, int gradedCount, BigDecimal averageFinalScore,
                         BigDecimal goodAnswerRate) {
    }

    record ReportDto(UUID vivaExamId, String title, int totalSessions, Map<String, Integer> sessionsByStatus,
                     Map<String, Integer> sessionsByStage, int evaluatedCount, int confirmedCount, ScoreStats finalTotals,
                     List<Bin> distribution, List<QuestionStats> questions, List<QuestionStats> hardestQuestions) {
    }
}
