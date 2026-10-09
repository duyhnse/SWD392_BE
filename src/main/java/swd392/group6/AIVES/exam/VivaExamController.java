package swd392.group6.AIVES.exam;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.exam.ExamDtos.AddStudentsReport;
import swd392.group6.AIVES.exam.ExamDtos.AddStudentsRequest;
import swd392.group6.AIVES.exam.ExamDtos.BlueprintRequest;
import swd392.group6.AIVES.exam.ExamDtos.CancelRequest;
import swd392.group6.AIVES.exam.ExamDtos.CreateExamRequest;
import swd392.group6.AIVES.exam.ExamDtos.ExamDetail;
import swd392.group6.AIVES.exam.ExamDtos.ExamSummary;
import swd392.group6.AIVES.exam.ExamDtos.GenerationResult;
import swd392.group6.AIVES.exam.ExamDtos.ImportReport;
import swd392.group6.AIVES.exam.ExamDtos.QuestionPoolRequest;
import swd392.group6.AIVES.exam.ExamDtos.RetakeRequest;
import swd392.group6.AIVES.exam.ExamDtos.RetakeResult;
import swd392.group6.AIVES.exam.ExamDtos.SessionRow;
import swd392.group6.AIVES.exam.ExamDtos.StudentView;
import swd392.group6.AIVES.exam.ExamDtos.UpdateExamRequest;
import swd392.group6.AIVES.user.User;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** FG2 buổi thi endpoints for lecturers (ADMIN read-only) — 15 §5.3. Authorization is enforced in the services. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class VivaExamController {

    private static final long MAX_IMPORT_BYTES = 2 * 1024 * 1024;

    private final VivaExamService exams;
    private final ExamStudentService students;
    private final ExamSessionService sessions;

    @GetMapping("/courses/{courseId}/viva-exams")
    PageResponse<ExamSummary> list(@PathVariable UUID courseId, @AuthenticationPrincipal User user,
                                   @RequestParam(required = false) List<ExamStatus> status,
                                   @RequestParam(required = false) String when,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return exams.list(courseId, user, status, when, page, size);
    }

    @PostMapping("/courses/{courseId}/viva-exams")
    ResponseEntity<ExamDetail> create(@PathVariable UUID courseId, @AuthenticationPrincipal User user,
                                      @Valid @RequestBody CreateExamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exams.create(courseId, request, user));
    }

    @GetMapping("/viva-exams/{id}")
    ExamDetail get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return exams.get(id, user);
    }

    @PatchMapping("/viva-exams/{id}")
    ExamDetail update(@PathVariable UUID id, @AuthenticationPrincipal User user,
                      @Valid @RequestBody UpdateExamRequest request) {
        return exams.update(id, request, user);
    }

    @DeleteMapping("/viva-exams/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        exams.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/viva-exams/{id}/blueprint")
    ExamDetail blueprint(@PathVariable UUID id, @AuthenticationPrincipal User user,
                         @Valid @RequestBody BlueprintRequest request) {
        return exams.replaceBlueprint(id, request, user);
    }

    @PutMapping("/viva-exams/{id}/question-pool")
    ExamDetail questionPool(@PathVariable UUID id, @AuthenticationPrincipal User user,
                            @Valid @RequestBody QuestionPoolRequest request) {
        return exams.replaceQuestionPool(id, request, user);
    }

    @GetMapping("/viva-exams/{id}/students")
    List<StudentView> students(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return students.list(id, user);
    }

    @PutMapping("/viva-exams/{id}/students")
    AddStudentsReport addStudents(@PathVariable UUID id, @AuthenticationPrincipal User user,
                                  @RequestBody AddStudentsRequest request) {
        return students.add(id, request.studentCodes(), request.usernames(), user);
    }

    @PostMapping("/viva-exams/{id}/students/import")
    ImportReport importStudents(@PathVariable UUID id, @AuthenticationPrincipal User user,
                                @RequestPart("file") MultipartFile file) throws IOException {
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "The class list must be at most 2 MB");
        }
        return students.importCsv(id, file.getInputStream(), user);
    }

    @DeleteMapping("/viva-exams/{id}/students/{studentId}")
    ResponseEntity<Void> removeStudent(@PathVariable UUID id, @PathVariable UUID studentId,
                                       @AuthenticationPrincipal User user) {
        students.remove(id, studentId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/viva-exams/{id}/generate-sessions")
    GenerationResult generate(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return sessions.generate(id, user);
    }

    @PostMapping("/viva-exams/{id}/reset-sessions")
    ExamDetail reset(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return sessions.reset(id, user);
    }

    @PostMapping("/viva-exams/{id}/open")
    ExamDetail open(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return sessions.open(id, user);
    }

    @PostMapping("/viva-exams/{id}/close")
    ExamDetail close(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return sessions.close(id, user);
    }

    @PostMapping("/viva-exams/{id}/cancel")
    ExamDetail cancel(@PathVariable UUID id, @AuthenticationPrincipal User user,
                      @RequestBody(required = false) CancelRequest request) {
        return sessions.cancel(id, request == null ? null : request.reason(), user);
    }

    @PostMapping("/viva-exams/{id}/retake")
    ResponseEntity<RetakeResult> retake(@PathVariable UUID id, @AuthenticationPrincipal User user,
                                        @Valid @RequestBody RetakeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessions.retake(id, request, user));
    }

    @PostMapping("/viva-exams/{id}/release-results")
    ExamDetail release(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return sessions.setResultsReleased(id, true, user);
    }

    @PostMapping("/viva-exams/{id}/unrelease-results")
    ExamDetail unrelease(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return sessions.setResultsReleased(id, false, user);
    }

    @GetMapping("/viva-exams/{id}/sessions")
    List<SessionRow> sessions(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return sessions.sessions(id, user);
    }
}
