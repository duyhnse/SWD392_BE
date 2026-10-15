package swd392.group6.AIVES.questionbank.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.DuplicateRubricRequest;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.RubricDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.RubricRequest;
import swd392.group6.AIVES.questionbank.internal.RubricService;
import swd392.group6.AIVES.user.User;

import java.util.List;
import java.util.UUID;

/** Course rubrics (15 §5.2). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
@RequiredArgsConstructor
class RubricController {

    private final RubricService service;

    @GetMapping("/courses/{courseId}/rubrics")
    public List<RubricDto> list(@PathVariable UUID courseId, @AuthenticationPrincipal User user) {
        return service.list(courseId, user);
    }

    @PostMapping("/courses/{courseId}/rubrics")
    @ResponseStatus(HttpStatus.CREATED)
    public RubricDto create(@PathVariable UUID courseId, @Valid @RequestBody RubricRequest request,
                            @AuthenticationPrincipal User user) {
        return service.create(courseId, request, user);
    }

    @GetMapping("/rubrics/{id}")
    public RubricDto get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.get(id, user);
    }

    @PutMapping("/rubrics/{id}")
    public RubricDto update(@PathVariable UUID id, @Valid @RequestBody RubricRequest request,
                            @AuthenticationPrincipal User user) {
        return service.update(id, request, user);
    }

    @DeleteMapping("/rubrics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        service.delete(id, user);
    }

    @PostMapping("/rubrics/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public RubricDto duplicate(@PathVariable UUID id, @Valid @RequestBody(required = false) DuplicateRubricRequest request,
                               @AuthenticationPrincipal User user) {
        return service.duplicate(id, request, user);
    }
}
