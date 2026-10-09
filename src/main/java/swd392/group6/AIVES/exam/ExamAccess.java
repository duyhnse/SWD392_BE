package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.User;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Loads a buổi thi or đề thi and applies 15 §6: assigned lecturers manage, ADMIN reads, everyone else gets 404 (BR-E7).
 */
@Component
@RequiredArgsConstructor
class ExamAccess {

    private final VivaExamRepository exams;
    private final ExamTemplateRepository templates;
    private final CourseAccessApi courseAccess;

    VivaExam read(UUID examId, User user) {
        VivaExam exam = find(examId);
        guard(() -> courseAccess.requireRead(exam.getCourseId(), user), ExamAccess::notFound);
        return exam;
    }

    VivaExam write(UUID examId, User user) {
        VivaExam exam = find(examId);
        guard(() -> courseAccess.requireWrite(exam.getCourseId(), user), ExamAccess::notFound);
        return exam;
    }

    ExamTemplate readTemplate(UUID templateId, User user) {
        ExamTemplate t = findTemplate(templateId);
        guard(() -> courseAccess.requireRead(t.getCourseId(), user), ExamAccess::templateNotFound);
        return t;
    }

    ExamTemplate writeTemplate(UUID templateId, User user) {
        ExamTemplate t = findTemplate(templateId);
        guard(() -> courseAccess.requireWrite(t.getCourseId(), user), ExamAccess::templateNotFound);
        return t;
    }

    private ExamTemplate findTemplate(UUID templateId) {
        return templates.findById(templateId).orElseThrow(ExamAccess::templateNotFound);
    }

    static ApiException templateNotFound() {
        return ApiException.notFound("EXAM_TEMPLATE_NOT_FOUND", "Exam template not found");
    }

    private VivaExam find(UUID examId) {
        return exams.findById(examId).orElseThrow(ExamAccess::notFound);
    }

    /** The course check answers COURSE_NOT_FOUND; for a resource the caller cannot see, say that is not found. */
    private static void guard(Runnable check, Supplier<ApiException> notFound) {
        try {
            check.run();
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw notFound.get();
            }
            throw e;
        }
    }

    static ApiException notFound() {
        return ApiException.notFound("VIVA_EXAM_NOT_FOUND", "Viva exam not found");
    }
}
