package swd392.group6.AIVES.questionbank.dto;

/** {@code moved}: draft / published questions now on the target rubric; {@code lockedKept}: drawn (locked) ones that stay. */
public record MoveRubricQuestionsResult(int moved, int lockedKept) {
}
