package swd392.group6.AIVES.interview;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public API of the interview module for grading (05 §13). */
public interface InterviewApi {

    /** One entry per session question (thread), in asking order, each with its turns (main first, then follow-ups). */
    List<ThreadInfo> getThreads(UUID sessionId);

    record ThreadInfo(UUID sessionQuestionId, UUID questionId, int orderNo, String status, List<TurnInfo> turns) {
    }

    record TurnInfo(UUID turnId, String turnType, int followupIndex, String questionText, String transcript, String status,
                    Integer responseDurationSec, Instant askedAt, String followupDecision, String answerAudioKey) {
    }
}
