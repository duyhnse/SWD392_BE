package swd392.group6.AIVES.exam.dto;

import java.util.List;

/** AC-C3: what happened to every pasted code / username. Nothing is ever created here (D28). */
public record AddStudentsReport(List<StudentRef> added, List<String> alreadyInExam, List<String> unknown,
                         List<String> notStudent, List<String> inactive, List<String> notInOriginal) {
}
