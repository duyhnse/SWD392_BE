package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.user.AdminUserDtos.LecturerSummary;

/** Shortcut for the lecturer picker: users with role LECTURER plus their course count (15 §5.1). */
@RestController
@RequestMapping("/api/v1/admin/lecturers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
class AdminLecturerController {

    private final AdminUserService adminUsers;

    @GetMapping
    public PageResponse<LecturerSummary> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Boolean active,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return adminUsers.lecturers(q, active, page, size);
    }
}
