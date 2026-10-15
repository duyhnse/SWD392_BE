package swd392.group6.AIVES.exam;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the exam module (07 §7, 15 §5.3). Status values are the DB enum names. */
public interface ExamApi {

    Optional<ExamInfo> getExam(UUID vivaExamId);

    Optional<SessionInfo> getSession(UUID sessionId);

    /** Main questions of a lượt thi in asking order. */
    List<SessionQuestionInfo> getSessionQuestions(UUID sessionId);

    record ExamInfo(UUID vivaExamId, UUID courseId, String title, String status, Instant windowStart, Instant windowEnd,
                    int timeLimitPerStudentSec, int mainQuestionCount, int maxFollowupsPerQuestion, UUID examinerId,
                    boolean resultsReleased, Instant resultsReleasedAt) {
    }

    record SessionInfo(UUID sessionId, UUID vivaExamId, UUID courseId, UUID studentId, UUID examinerId, String status,
                       String endReason, String cancelReason, Instant startedAt, Instant deadlineAt, Instant endedAt) {
    }

    record SessionQuestionInfo(UUID sessionQuestionId, UUID questionId, int orderNo, String status) {
    }
}
