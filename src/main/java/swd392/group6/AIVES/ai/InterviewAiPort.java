package swd392.group6.AIVES.ai;

import swd392.group6.AIVES.common.Language;

import java.util.List;

/**
 * The latency-critical step of the viva (WF3, contract 16 §3.2 {@code POST /v1/interview/process-answer}): one answer
 * clip → transcript → analysis of the whole thread → proposed follow-up (+ its audio). The AI node only proposes;
 * the exam runtime on node 1 decides whether to ask it (D03).
 */
public interface InterviewAiPort {

    /**
     * Never throws for a failed analysis: {@link AnswerResult#analysis()} is then null and {@code analysisError}
     * says why, so the runtime can still store the transcript and move on (NEXT_AI_ERROR).
     *
     * @throws AiUnavailableException when not even a transcript could be produced
     */
    AnswerResult processAnswer(AnswerRequest request);

    /**
     * @param requestId      idempotency / log correlation id (the turn id)
     * @param hotwords       course terms for STT (never student data, BR-I8)
     * @param priorTurns     earlier turns of the same thread, oldest first (main question first)
     * @param mockTranscript dev/test only, ignored by the AI node
     */
    record AnswerRequest(String requestId, Language language, byte[] audio, String audioContentType,
                         List<String> hotwords, String mainQuestion, String referenceAnswer, List<PriorTurn> priorTurns,
                         String currentQuestionText, int followupsUsed, int maxFollowups, boolean wantFollowUpAudio,
                         String mockTranscript) {
    }

    record PriorTurn(String type, String questionText, String transcript) {
    }

    record AnswerResult(Transcript transcript, Analysis analysis, SynthesizedAudio followUpAudio, String analysisError) {
    }

    /**
     * P-ANALYZE output (contract 16 §3.2). {@code keywordStuffingScore} 0–1 and {@code selfCorrections} support the
     * shotgunning / self-correction rule (D52); they are evidence for the lecturer, not a score.
     */
    record Analysis(String coverage, List<String> coveredPoints, List<String> missingPoints, boolean vague,
                    boolean contradictory, boolean bluffing, boolean offTopicOrWrong, double keywordStuffingScore,
                    List<SelfCorrection> selfCorrections, String followUpQuestion, String rationale, String model) {
    }

    record SelfCorrection(String retracted, String corrected) {
    }
}
