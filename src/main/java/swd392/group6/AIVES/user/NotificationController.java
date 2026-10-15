package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.user.NotificationService.NotificationResponse;

import java.util.UUID;

/** The caller's own notifications, newest first (15 §5.1). */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
class NotificationController {

    private final NotificationService notifications;

    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal User currentUser,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return notifications.list(currentUser.getUserId(), unreadOnly, page, size);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        notifications.markRead(currentUser.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal User currentUser) {
        notifications.markAllRead(currentUser.getUserId());
        return ResponseEntity.noContent().build();
    }
}
