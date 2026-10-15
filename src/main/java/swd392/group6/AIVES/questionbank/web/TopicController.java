package swd392.group6.AIVES.questionbank.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.CreateTopicRequest;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.TermsDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.TopicDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.UpdateTopicRequest;
import swd392.group6.AIVES.questionbank.internal.TopicService;
import swd392.group6.AIVES.user.User;

import java.util.List;
import java.util.UUID;

/** Topics and course terms (15 §5.2). ADMIN read-only, assigned lecturers read/write. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
@RequiredArgsConstructor
class TopicController {

    private final TopicService service;

    @GetMapping("/courses/{courseId}/topics")
    public List<TopicDto> list(@PathVariable UUID courseId, @AuthenticationPrincipal User user) {
        return service.list(courseId, user);
    }

    @PostMapping("/courses/{courseId}/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public TopicDto create(@PathVariable UUID courseId, @Valid @RequestBody CreateTopicRequest request,
                           @AuthenticationPrincipal User user) {
        return service.create(courseId, request, user);
    }

    @PatchMapping("/topics/{id}")
    public TopicDto update(@PathVariable UUID id, @Valid @RequestBody UpdateTopicRequest request,
                           @AuthenticationPrincipal User user) {
        return service.update(id, request, user);
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        service.delete(id, user);
    }

    @GetMapping("/courses/{courseId}/terms")
    public TermsDto terms(@PathVariable UUID courseId, @AuthenticationPrincipal User user) {
        return service.getTerms(courseId, user);
    }

    @PutMapping("/courses/{courseId}/terms")
    public TermsDto replaceTerms(@PathVariable UUID courseId, @Valid @RequestBody TermsDto request,
                                 @AuthenticationPrincipal User user) {
        return service.replaceTerms(courseId, request, user);
    }
}
