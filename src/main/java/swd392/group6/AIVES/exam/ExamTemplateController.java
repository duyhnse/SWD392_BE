package swd392.group6.AIVES.exam;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.exam.ExamDtos.ArchiveRequest;
import swd392.group6.AIVES.exam.ExamDtos.CreateTemplateRequest;
import swd392.group6.AIVES.exam.ExamDtos.DuplicateTemplateRequest;
import swd392.group6.AIVES.exam.ExamDtos.QuestionPoolRequest;
import swd392.group6.AIVES.exam.ExamDtos.TemplateDetail;
import swd392.group6.AIVES.exam.ExamDtos.TemplateItemsRequest;
import swd392.group6.AIVES.exam.ExamDtos.TemplateSummary;
import swd392.group6.AIVES.exam.ExamDtos.UpdateTemplateRequest;
import swd392.group6.AIVES.user.User;

import java.util.List;
import java.util.UUID;

/** Đề thi endpoints (D45, 15 §5.3). Assigned lecturers manage, ADMIN reads; checks are in the service. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
@RequiredArgsConstructor
class ExamTemplateController {

    private final ExamTemplateService service;

    @GetMapping("/courses/{courseId}/exam-templates")
    List<TemplateSummary> list(@PathVariable UUID courseId, @AuthenticationPrincipal User user,
                               @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(courseId, includeArchived, user);
    }

    @PostMapping("/courses/{courseId}/exam-templates")
    ResponseEntity<TemplateDetail> create(@PathVariable UUID courseId, @AuthenticationPrincipal User user,
                                          @Valid @RequestBody CreateTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(courseId, request, user));
    }

    @GetMapping("/exam-templates/{id}")
    TemplateDetail get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.get(id, user);
    }

    @PutMapping("/exam-templates/{id}")
    TemplateDetail update(@PathVariable UUID id, @AuthenticationPrincipal User user,
                          @Valid @RequestBody UpdateTemplateRequest request) {
        return service.update(id, request, user);
    }

    @DeleteMapping("/exam-templates/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        service.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/exam-templates/{id}/items")
    TemplateDetail items(@PathVariable UUID id, @AuthenticationPrincipal User user,
                         @Valid @RequestBody TemplateItemsRequest request) {
        return service.replaceItems(id, request.items(), user);
    }

    @PutMapping("/exam-templates/{id}/question-pool")
    TemplateDetail questionPool(@PathVariable UUID id, @AuthenticationPrincipal User user,
                                @Valid @RequestBody QuestionPoolRequest request) {
        return service.replaceQuestionPool(id, request, user);
    }

    @PostMapping("/exam-templates/{id}/duplicate")
    ResponseEntity<TemplateDetail> duplicate(@PathVariable UUID id, @AuthenticationPrincipal User user,
                                             @Valid @RequestBody(required = false) DuplicateTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.duplicate(id, request == null ? null : request.title(), user));
    }

    @PostMapping("/exam-templates/{id}/archive")
    TemplateDetail archive(@PathVariable UUID id, @AuthenticationPrincipal User user,
                           @RequestBody(required = false) ArchiveRequest request) {
        return service.setArchived(id, request == null || request.archived(), user);
    }
}
