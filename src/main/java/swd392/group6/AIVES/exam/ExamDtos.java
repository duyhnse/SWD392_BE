package swd392.group6.AIVES.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request / response bodies of the đề thi, buổi thi and lượt thi endpoints (15 §5.3). */
final class ExamDtos {

    private ExamDtos() {
    }

    // ---- đề thi (exam templates) ----------------------------------------------------------------------------

    /** Missing numbers take the {@code exam_templates} defaults; the language defaults to the course language. */
    record CreateTemplateRequest(@NotBlank @Size(max = 200) String title, String description, Language language,
                                 Integer maxFollowupsPerQuestion, Integer maxAnswerSec, Integer silenceWarningSec,
                                 Boolean showQuestionText,
                                 @DecimalMin("0") @DecimalMax("10") BigDecimal passScore, UUID rubricId,
                                 List<@Valid @NotNull TemplateItemRequest> items) {
    }

    /** Full replacement of the template's own fields (rows and pool have their own endpoints). */
    record UpdateTemplateRequest(@NotNull Integer version, @NotBlank @Size(max = 200) String title, String description,
                                 @NotNull Language language, @NotNull Integer maxFollowupsPerQuestion,
                                 @NotNull Integer maxAnswerSec, @NotNull Integer silenceWarningSec,
                                 @NotNull Boolean showQuestionText,
                                 @DecimalMin("0") @DecimalMax("10") BigDecimal passScore, UUID rubricId) {
    }

    /** {@code secondsPerQuestion} null = default for the Bloom level ({@code exam.seconds.*} settings). */
    record TemplateItemRequest(UUID chapterId, BloomLevel bloomLevel, @NotNull @Min(1) @Max(10) Integer count,
                               Integer secondsPerQuestion, UUID rubricId) {
    }

    record TemplateItemsRequest(@NotNull List<@Valid @NotNull TemplateItemRequest> items) {
    }

    record QuestionPoolRequest(@NotNull QuestionPoolMode mode, List<UUID> questionIds) {
    }

    record DuplicateTemplateRequest(@Size(max = 200) String title) {
    }

    record ArchiveRequest(boolean archived) {
    }

    /** {@code available} = PUBLISHED questions of the pool that match the row right now. */
    record TemplateItemView(UUID id, UUID chapterId, Integer chapterNo, String chapterTitle, BloomLevel bloomLevel,
                            int count, int secondsPerQuestion, UUID rubricId, int sortOrder, int available) {
    }

    record TemplateDetail(UUID id, UUID courseId, String title, String description, Language language,
                          int maxFollowupsPerQuestion, int maxAnswerSec, int silenceWarningSec,
                          boolean showQuestionText, BigDecimal passScore, UUID rubricId,
                          QuestionPoolMode questionPoolMode, List<UUID> selectedQuestionIds,
                          List<TemplateItemView> items, int mainQuestionCount, int totalDurationSec,
                          boolean poolSufficient, boolean locked, boolean archived, long usedByExamCount,
                          UUID createdBy, int version, Instant createdAt, Instant updatedAt) {
    }

    record TemplateSummary(UUID id, UUID courseId, String title, int mainQuestionCount, int totalDurationSec,
                           BigDecimal passScore, boolean locked, boolean archived, long usedByExamCount, int version,
                           Instant updatedAt) {
    }

    // ---- buổi thi -------------------------------------------------------------------------------------------

    /** Missing connection rules take the {@code viva_exams} defaults; the examiner defaults to the caller. */
    record CreateExamRequest(@NotNull UUID templateId, @NotBlank @Size(max = 200) String title, String description,
                             String instructions, @Size(max = 150) String location, UUID examinerId,
                             @NotNull Instant checkinOpensAt, @NotNull Instant checkinClosesAt,
                             Integer reconnectGraceSec, Integer maxDisconnects, Integer maxFrozenSec,
                             Integer replaceMainAfterSec) {
    }

    /** {@code null} = unchanged; an empty string clears description / instructions / location. */
    record UpdateExamRequest(@NotNull Integer version, UUID templateId, @Size(max = 200) String title,
                             String description, String instructions, @Size(max = 150) String location,
                             UUID examinerId, Instant checkinOpensAt, Instant checkinClosesAt,
                             Integer reconnectGraceSec, Integer maxDisconnects, Integer maxFrozenSec,
                             Integer replaceMainAfterSec) {

        /** Fields that cannot change once students may check in. */
        boolean touchesFrozenFields() {
            return templateId != null || examinerId != null || checkinOpensAt != null || reconnectGraceSec != null
                    || maxDisconnects != null || maxFrozenSec != null || replaceMainAfterSec != null;
        }
    }

    /** Each field is a JSON array or one pasted string ("SE190001, SE190002\nSE190003"). */
    record AddStudentsRequest(Object studentCodes, Object usernames) {
    }

    record CancelRequest(String reason) {
    }

    record RetakeRequest(@Size(max = 200) String title, @NotNull Instant checkinOpensAt,
                         @NotNull Instant checkinClosesAt, Object studentCodes, Object usernames) {
    }

    record ExamSummary(UUID id, UUID courseId, UUID templateId, String templateTitle, String title, ExamStatus status,
                       Instant checkinOpensAt, Instant checkinClosesAt, int mainQuestionCount, int durationSec,
                       long studentCount, long checkedInCount, long completedCount, long confirmedCount,
                       boolean resultsReleased, UUID retakeOfVivaExamId, int version) {
    }

    record ExamDetail(UUID id, UUID courseId, String title, String description, String instructions, String location,
                      ExamStatus status, UUID createdBy, UUID examinerId, TemplateSummary template,
                      Instant checkinOpensAt, Instant checkinClosesAt, int durationSec, Instant lastPossibleEndAt,
                      int reconnectGraceSec, int maxDisconnects, int maxFrozenSec, int replaceMainAfterSec,
                      long studentCount, Map<ExamStage, Long> stageCounts, boolean resultsReleased,
                      Instant resultsReleasedAt, UUID retakeOfVivaExamId, String cancelReason, int version,
                      Instant createdAt, Instant updatedAt) {
    }

    /** Result of publish / pool check: per template row, whether the pool can serve every student. */
    record PoolCheck(boolean sufficient, List<QuestionSelector.RowShortage> shortages,
                     List<QuestionSelector.Warning> warnings) {
    }

    record PublishResult(ExamDetail exam, List<QuestionSelector.Warning> warnings) {
    }

    record StudentView(UUID studentId, String username, String fullName, String studentCode, int seqNo,
                       Instant addedAt, UUID attemptId, String attemptStatus, ExamStage stage) {
    }

    record StudentRef(UUID studentId, String username, String studentCode, String fullName, int seqNo) {
    }

    /** AC-C3: what happened to every pasted code / username. Nothing is ever created here (D28). */
    record AddStudentsReport(List<StudentRef> added, List<String> alreadyInExam, List<String> unknown,
                             List<String> notStudent, List<String> inactive, List<String> notInOriginal) {
    }

    record ImportRowError(int row, String field, String code, String message) {
    }

    record ImportReport(int total, int created, List<StudentRef> added, List<String> alreadyInExam,
                        List<String> notStudent, List<ImportRowError> errors) {
    }

    record RetakeResult(ExamDetail exam, AddStudentsReport students) {
    }

    // ---- lượt thi (attempts) --------------------------------------------------------------------------------

    record CheckInRequest(boolean consentRecording, @Size(max = 300) String clientInfo) {
    }

    /** One roster row of a buổi thi as the lecturer sees it, with the attempt when there is one. */
    record AttemptRow(UUID studentId, String username, String fullName, String studentCode, int seqNo,
                      ExamStage stage, UUID attemptId, String status, String endReason, Instant startedAt,
                      Instant deadlineAt, Instant endedAt, int disconnectCount, int frozenSecTotal,
                      UUID evaluationId, String evaluationStatus, BigDecimal finalTotalScore) {
    }

    /** A drawn question with its snapshot (D49) — lecturers only. */
    record AttemptQuestionView(UUID attemptQuestionId, UUID questionId, int orderNo, String status, Integer chapterNo,
                               String chapterTitle, BloomLevel bloomLevel, String content, String referenceAnswer,
                               int timeBudgetSec, int timeUsedSec, String rubricName, Instant startedAt,
                               Instant endedAt, UUID replacesAttemptQuestionId, String voidReason) {
    }

    record AttemptDetail(UUID attemptId, UUID vivaExamId, UUID courseId, UUID studentId, String username,
                         String fullName, String studentCode, String status, String endReason, String cancelReason,
                         Instant startedAt, Instant deadlineAt, Instant endedAt, Instant consentRecordedAt,
                         int disconnectCount, int frozenSecTotal, String clientInfo, Long selectionSeed,
                         List<AttemptQuestionView> questions) {
    }

    /**
     * A buổi thi as the student sees it (15 §2.2) — never with question content. {@code attemptId} is set once
     * the student checked in.
     */
    record MyExam(UUID vivaExamId, UUID courseId, String courseCode, String courseName, String title,
                  String description, String instructions, String location, Language language, ExamStage stage,
                  ResultStatus resultStatus, Instant checkinOpensAt, Instant checkinClosesAt, int durationSec,
                  int mainQuestionCount, int maxFollowupsPerQuestion, boolean showQuestionText, UUID attemptId,
                  Instant startedAt, Instant deadlineAt, Instant endedAt, int attemptsAllowed, int attemptsUsed,
                  UUID evaluationId) {
    }
}
