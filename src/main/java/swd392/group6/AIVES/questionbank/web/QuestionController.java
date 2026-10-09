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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.questionbank.BloomLevel;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.CreateQuestionRequest;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.PublishRequest;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.PublishResult;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.QuestionDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.QuestionFilter;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.QuestionSummaryDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.UpdateQuestionRequest;
import swd392.group6.AIVES.questionbank.internal.QuestionOrigin;
import swd392.group6.AIVES.questionbank.internal.QuestionService;
import swd392.group6.AIVES.questionbank.internal.QuestionStatus;
import swd392.group6.AIVES.user.User;

import java.util.List;
import java.util.UUID;

/** Questions and their state changes (15 §5.2, 03 §2.1). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
@RequiredArgsConstructor
class QuestionController {

    private final QuestionService service;

    @GetMapping("/courses/{courseId}/questions")
    public PageResponse<QuestionSummaryDto> list(@PathVariable UUID courseId,
                                                 @RequestParam(required = false) List<QuestionStatus> status,
                                                 @RequestParam(required = false) List<UUID> chapterId,
                                                 @RequestParam(required = false) List<BloomLevel> bloomLevel,
                                                 @RequestParam(required = false) List<QuestionOrigin> origin,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @AuthenticationPrincipal User user) {
        return service.list(courseId, new QuestionFilter(status, chapterId, bloomLevel, origin, q), page, size, user);
    }

    @PostMapping("/courses/{courseId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionDto create(@PathVariable UUID courseId, @Valid @RequestBody CreateQuestionRequest request,
                              @AuthenticationPrincipal User user) {
        return service.create(courseId, request, user);
    }

    @GetMapping("/questions/{id}")
    public QuestionDto get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.get(id, user);
    }

    @PutMapping("/questions/{id}")
    public QuestionDto update(@PathVariable UUID id, @Valid @RequestBody UpdateQuestionRequest request,
                              @AuthenticationPrincipal User user) {
        return service.update(id, request, user);
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        service.delete(id, user);
    }

    @PostMapping("/questions/{id}/discard")
    public QuestionDto discard(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.discard(id, user);
    }

    @PostMapping("/questions/{id}/restore")
    public QuestionDto restore(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.restore(id, user);
    }

    @PostMapping("/questions/{id}/unpublish")
    public QuestionDto unpublish(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.unpublish(id, user);
    }

    @PostMapping("/questions/{id}/successor")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionDto successor(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.successor(id, user);
    }

    @PostMapping("/questions/publish")
    public PublishResult publish(@Valid @RequestBody PublishRequest request, @AuthenticationPrincipal User user) {
        return service.publish(request.questionIds(), user);
    }
}
