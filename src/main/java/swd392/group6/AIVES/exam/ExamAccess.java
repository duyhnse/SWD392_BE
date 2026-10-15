package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.User;

import java.util.UUID;

/**
 * Loads a buổi thi and applies 15 §6: assigned lecturers manage, ADMIN reads, everyone else gets 404 (BR-E7).
 */
@Component
@RequiredArgsConstructor
class ExamAccess {

    private final VivaExamRepository exams;
    private final CourseAccessApi courseAccess;

    VivaExam read(UUID examId, User user) {
        VivaExam exam = find(examId);
        guard(() -> courseAccess.requireRead(exam.getCourseId(), user));
        return exam;
    }

    VivaExam write(UUID examId, User user) {
        VivaExam exam = find(examId);
        guard(() -> courseAccess.requireWrite(exam.getCourseId(), user));
        return exam;
    }

    private VivaExam find(UUID examId) {
        return exams.findById(examId).orElseThrow(ExamAccess::notFound);
    }

    /** The course check answers COURSE_NOT_FOUND; for an exam the caller cannot see, say the exam is not found. */
    private static void guard(Runnable check) {
        try {
            check.run();
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw notFound();
            }
            throw e;
        }
    }

    static ApiException notFound() {
        return ApiException.notFound("VIVA_EXAM_NOT_FOUND", "Viva exam not found");
    }
}
