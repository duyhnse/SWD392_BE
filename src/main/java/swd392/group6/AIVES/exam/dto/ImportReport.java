package swd392.group6.AIVES.exam.dto;

import java.util.List;

public record ImportReport(int total, int created, List<StudentRef> added, List<String> alreadyInExam,
                    List<String> notStudent, List<ImportRowError> errors) {
}
