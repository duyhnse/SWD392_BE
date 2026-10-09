package swd392.group6.AIVES.exam;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the exam module for interview and grading (07 §7, 15 §5.3). Status values are the DB enum names.
 * Attempt questions carry the snapshot taken at check-in (D49): never read the question bank to grade or ask.
 */
public interface ExamApi {

    Optional<ExamInfo> getExam(UUID vivaExamId);

    Optional<AttemptInfo> getAttempt(UUID attemptId);

    /** Questions of an attempt in asking order, VOIDED ones included (they keep their order number). */
    List<AttemptQuestionInfo> getAttemptQuestions(UUID attemptId);

    /** Attempts of a buổi thi with the roster order (grading reports, exports). */
    List<AttemptInfo> listAttempts(UUID vivaExamId);

    record ExamInfo(UUID vivaExamId, UUID courseId, UUID templateId, String title, String status, Instant checkinOpensAt,
                    Instant checkinClosesAt, int mainQuestionCount, int maxFollowupsPerQuestion, int maxAnswerSec,
                    int silenceWarningSec, boolean showQuestionText, String language, BigDecimal passScore,
                    UUID examinerId, boolean resultsReleased, Instant resultsReleasedAt, int reconnectGraceSec,
                    int maxDisconnects, int maxFrozenSec, int replaceMainAfterSec) {
    }

    record AttemptInfo(UUID attemptId, UUID vivaExamId, UUID courseId, UUID studentId, UUID examinerId, String status,
                       String endReason, Instant startedAt, Instant deadlineAt, Instant endedAt, int disconnectCount,
                       int frozenSecTotal, Integer seqNo) {
    }

    /**
     * @param rubricSnapshotJson {@code {rubricId, name, criteria:[{criterionId, name, description, maxScore,
     *                           weightPercent, sortOrder}]}} as stored at check-in
     */
    record AttemptQuestionInfo(UUID attemptQuestionId, UUID questionId, int orderNo, String status, Integer chapterNo,
                               String chapterTitle, String bloomLevel, String language, String content,
                               String referenceAnswer, String rubricSnapshotJson, int timeBudgetSec, int timeUsedSec,
                               String voidReason) {
    }
}
