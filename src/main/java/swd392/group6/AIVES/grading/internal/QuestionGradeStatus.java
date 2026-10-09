package swd392.group6.AIVES.grading.internal;

/** {@code question_grades.status} (03 §2.5). */
enum QuestionGradeStatus {
    PENDING_AI,
    AI_SCORED,
    AI_FAILED,
    MISSING_DATA,
    NOT_ASKED,
    CONFIRMED
}
