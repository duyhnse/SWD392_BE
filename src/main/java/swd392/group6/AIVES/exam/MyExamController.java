package swd392.group6.AIVES.exam;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.exam.ExamDtos.CheckInRequest;
import swd392.group6.AIVES.exam.ExamDtos.MyExam;
import swd392.group6.AIVES.user.User;

import java.util.Set;
import java.util.UUID;

/** Student view of their buổi thi and check-in (15 §2.2, D48). */
@RestController
@RequestMapping("/api/v1/me/viva-exams")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
class MyExamController {

    private final MyExamService service;
    private final CheckInService checkIn;

    @GetMapping
    PageResponse<MyExam> list(@AuthenticationPrincipal User user,
                              @RequestParam(required = false) Set<ExamStage> stage,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "50") int size) {
        return service.list(user, stage, page, size);
    }

    @GetMapping("/{id}")
    MyExam get(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return service.get(user, id);
    }

    /** Starts the student's only attempt: questions are drawn now (D48). Repeating it returns the running attempt. */
    @PostMapping("/{id}/check-in")
    MyExam checkIn(@AuthenticationPrincipal User user, @PathVariable UUID id,
                   @Valid @RequestBody(required = false) CheckInRequest request) {
        return checkIn.checkIn(id, request, user);
    }
}
