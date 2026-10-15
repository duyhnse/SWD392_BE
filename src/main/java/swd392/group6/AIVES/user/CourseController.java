package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.user.CourseDtos.CourseDetail;
import swd392.group6.AIVES.user.CourseDtos.CourseResponse;

import java.util.UUID;

/** Course reading for ADMIN (all) and LECTURER (assigned only); students have no course screens (15 §5.1). */
@RestController
@RequestMapping("/api/v1/courses")
@PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
@RequiredArgsConstructor
class CourseController {

    private final CourseService courseService;

    @GetMapping
    public PageResponse<CourseResponse> list(@AuthenticationPrincipal User currentUser,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) Boolean active,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return courseService.list(currentUser, q, active, page, size);
    }

    /** Unassigned lecturers get 404, not 403 (09 §1). */
    @GetMapping("/{id}")
    public CourseDetail get(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        return courseService.get(currentUser, id);
    }
}
