package swd392.group6.AIVES.ai;

/** Asynchronous work on the AI node (contract 16 §4). Values match {@code ai_jobs.job_type}. */
public enum AiJobType {
    /** P-GRADE: rubric scores for one thread (WF4). */
    GRADE_THREAD,
    /** P-SUMMARY: overall comment of an evaluation (WF4). */
    SUMMARIZE_EVALUATION,
    /** Parse, chunk and embed one course material (WF1 RAG). */
    INDEX_MATERIAL,
    /** P-GEN: draft questions for one chapter from retrieved chunks (WF1 RAG). */
    GENERATE_QUESTIONS
}
