package swd392.group6.AIVES.ai;

/** Which prompt of 10_AI_PROMPTS.md a request belongs to. */
public enum LlmTask {
    /** P-GEN — WF1 question generation. */
    GENERATE_QUESTIONS,
    /** P-ANALYZE — WF3 answer analysis + follow-up proposal. */
    ANALYZE_ANSWER,
    /** P-GRADE — WF4 rubric scoring of one thread. */
    GRADE_THREAD,
    /** P-SUMMARY — WF4 overall evaluation comment. */
    SUMMARIZE_EVALUATION
}
