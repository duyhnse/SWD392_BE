package swd392.group6.AIVES.interview.internal;

import swd392.group6.AIVES.ai.InterviewAiPort.Analysis;

/**
 * Follow-up decision (05 §5, D03): the AI node only proposes; this pure function decides. No AI in its tests.
 */
final class FollowupDecider {

    /** Answering time a follow-up needs; below it the thread moves on (05 §6). */
    static final int MIN_REMAINING_SEC_FOR_FOLLOWUP = 30;
    /** BR-I3: one short question. */
    static final int MAX_FOLLOWUP_WORDS = 40;

    private FollowupDecider() {
    }

    /** What forces NEXT before any AI analysis — then the analysis is skipped to save time (05 §5). */
    static String forced(int followupIndex, int maxFollowups, int threadRemainingSec, int attemptRemainingSec) {
        if (followupIndex >= maxFollowups) {
            return "NEXT_LIMIT";
        }
        if (Math.min(threadRemainingSec, attemptRemainingSec) < MIN_REMAINING_SEC_FOR_FOLLOWUP) {
            return "NEXT_TIME";
        }
        return null;
    }

    static String decide(Analysis a) {
        if (a == null) {
            return "NEXT_AI_ERROR";
        }
        if (a.offTopicOrWrong()) {
            return "NEXT_WRONG";
        }
        boolean incomplete = !"COMPLETE".equals(a.coverage());
        boolean unclear = a.contradictory() || a.vague() || a.bluffing();
        if (incomplete || unclear) {
            if (a.followUpQuestion() == null || a.followUpQuestion().isBlank()) {
                return "NEXT_COMPLETE";
            }
            return validFollowUp(a.followUpQuestion()) ? "FOLLOW_UP" : "NEXT_AI_ERROR";
        }
        return "NEXT_COMPLETE";
    }

    /** BR-I3 checks node 1 can make without AI: non-empty, one short question. */
    static boolean validFollowUp(String text) {
        String t = text.strip();
        return !t.isEmpty() && t.split("\\s+").length <= MAX_FOLLOWUP_WORDS;
    }

    /** EX-5: an empty or noise-only transcript (< 2 words) counts as no answer. */
    static boolean isNoAnswer(String transcript) {
        return transcript == null || transcript.isBlank() || transcript.strip().split("\\s+").length < 2;
    }
}
