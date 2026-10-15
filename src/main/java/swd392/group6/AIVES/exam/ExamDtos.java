package swd392.group6.AIVES.exam;

import jakarta.validation.Valid;
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

/** Request / response bodies of the buổi thi endpoints (15 §5.3). */
final class ExamDtos {

    private ExamDtos() {
    }

    // ---- requests -------------------------------------------------------------------------------------------

    /** Missing numbers take the defaults of {@code viva_exams}; the examiner defaults to the caller. */
    record CreateExamRequest(@NotBlank @Size(max = 200) String title, String description, String instructions,
                             @Size(max = 150) String location, UUID examinerId,
                             @NotNull Instant windowStart, @NotNull Instant windowEnd, Language language,
                             Integer mainQuestionCount, Integer maxFollowupsPerQuestion, Integer timeLimitPerStudentSec,
                             Integer answerTimeLimitSec, Integer silenceWarningSec, Integer reconnectGraceSec,
                             List<UUID> topicIds, List<BloomLevel> bloomLevels, Boolean showQuestionText) {
    }

    /** {@code null} = unchanged; an empty string clears description / instructions / location. */
    record UpdateExamRequest(@NotNull Integer version, @Size(max = 200) String title, String description,
                             String instructions, @Size(max = 150) String location, UUID examinerId,
                             Instant windowStart, Instant windowEnd, Language language,
                             Integer mainQuestionCount, Integer maxFollowupsPerQuestion, Integer timeLimitPerStudentSec,
                             Integer answerTimeLimitSec, Integer silenceWarningSec, Integer reconnectGraceSec,
                             List<UUID> topicIds, List<BloomLevel> bloomLevels, Boolean showQuestionText) {

        boolean touchesConfig() {
            return examinerId != null || windowStart != null || windowEnd != null || language != null
                    || mainQuestionCount != null || maxFollowupsPerQuestion != null || timeLimitPerStudentSec != null
                    || answerTimeLimitSec != null || silenceWarningSec != null || reconnectGraceSec != null
                    || topicIds != null || bloomLevels != null || showQuestionText != null;
        }
    }

    record BlueprintRequest(@NotNull List<@Valid @NotNull BlueprintItemRequest> items) {
    }

    record BlueprintItemRequest(UUID topicId, BloomLevel bloomLevel, @NotNull @Min(1) @Max(10) Integer count) {
    }

    record QuestionPoolRequest(@NotNull QuestionPoolMode mode, List<UUID> questionIds) {
    }

    /** Each field is a JSON array or one pasted string ("SE190001, SE190002\nSE190003"). */
    record AddStudentsRequest(Object studentCodes, Object usernames) {
    }

    record CancelRequest(String reason) {
    }

    record RetakeRequest(@Size(max = 200) String title, @NotNull Instant windowStart, @NotNull Instant windowEnd,
                         Object studentCodes, Object usernames) {
    }

    // ---- responses ------------------------------------------------------------------------------------------

    record ExamSummary(UUID id, UUID courseId, String title, ExamStatus status, Instant windowStart, Instant windowEnd,
                       int mainQuestionCount, int timeLimitPerStudentSec, long studentCount, long completedCount,
                       long confirmedCount, boolean resultsReleased, UUID retakeOfVivaExamId, int version) {
    }

    record ExamDetail(UUID id, UUID courseId, String title, String description, String instructions, String location,
                      ExamStatus status, UUID createdBy, UUID examinerId, Instant windowStart, Instant windowEnd,
                      Language language, int mainQuestionCount, int maxFollowupsPerQuestion,
                      int timeLimitPerStudentSec, int answerTimeLimitSec, int silenceWarningSec, int reconnectGraceSec,
                      List<UUID> topicIds, List<BloomLevel> bloomLevels, String selectionStrategy,
                      boolean showQuestionText, List<BlueprintItemView> blueprint, QuestionPoolView questionPool,
                      long studentCount, Map<SessionStage, Long> stageCounts, boolean resultsReleased,
                      Instant resultsReleasedAt, UUID retakeOfVivaExamId, String cancelReason, int version,
                      Instant createdAt, Instant updatedAt) {
    }

    record BlueprintItemView(UUID id, UUID topicId, BloomLevel bloomLevel, int count, int sortOrder) {
    }

    /** {@code availableCount} = PUBLISHED questions the generation would draw from right now. */
    record QuestionPoolView(QuestionPoolMode mode, List<UUID> questionIds, int availableCount) {
    }

    record StudentView(UUID studentId, String username, String fullName, String studentCode, int seqNo,
                       Instant addedAt, UUID sessionId, String sessionStatus, SessionStage stage) {
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

    record GeneratedQuestion(UUID sessionQuestionId, UUID questionId, int orderNo, UUID topicId, BloomLevel bloomLevel,
                             String content) {
    }

    record GeneratedSession(UUID sessionId, UUID studentId, String username, String studentCode, int seqNo,
                            List<GeneratedQuestion> questions) {
    }

    record GenerationResult(ExamStatus status, List<GeneratedSession> sessions, List<QuestionSelector.Warning> warnings) {
    }

    record SessionRow(UUID sessionId, UUID studentId, String username, String fullName, String studentCode,
                      Integer seqNo, String status, String cancelReason, String endReason, SessionStage stage,
                      Instant startedAt, Instant deadlineAt, Instant endedAt, UUID evaluationId,
                      String evaluationStatus, BigDecimal finalTotalScore) {
    }

    /** A lượt thi as the student sees it (15 §2.2) — never with question content. */
    record MySession(UUID sessionId, UUID vivaExamId, UUID courseId, String courseCode, String courseName,
                     String title, String description, String instructions, String location, Language language,
                     SessionStage stage, ResultStatus resultStatus, Instant windowStart, Instant windowEnd,
                     int timeLimitPerStudentSec, int mainQuestionCount, int maxFollowupsPerQuestion,
                     Instant startedAt, Instant deadlineAt, Instant endedAt, int attemptsAllowed, int attemptsUsed,
                     UUID evaluationId) {
    }

    record RetakeResult(ExamDetail exam, AddStudentsReport students) {
    }
}
