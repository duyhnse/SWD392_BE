package swd392.group6.AIVES.exam;

/** Where the questions of a buổi thi come from (15 §3). */
enum QuestionPoolMode {
    /** All PUBLISHED questions of the course. */
    COURSE_BANK,
    /** Only the questions the lecturer picked ({@code viva_exam_questions}). */
    SELECTED
}
