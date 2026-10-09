package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.exam.ExamDtos.MySession;
import swd392.group6.AIVES.user.User;

import java.util.Set;
import java.util.UUID;

/** Student view of their own lượt thi (15 §2.2, §5.3). */
@RestController
@RequestMapping("/api/v1/me/sessions")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
class MySessionController {

    private final MySessionService service;

    @GetMapping
    PageResponse<MySession> list(@AuthenticationPrincipal User user,
                                 @RequestParam(required = false) Set<SessionStage> stage,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return service.list(user, stage, page, size);
    }

    @GetMapping("/{id}")
    MySession get(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return service.get(user, id);
    }
}
