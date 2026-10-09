package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.user.SettingsService.SettingResponse;
import swd392.group6.AIVES.user.SettingsService.UpdateSettingsRequest;

import java.util.List;

/** Language & speech configuration (FG7), ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
class AdminSettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public List<SettingResponse> list() {
        return settingsService.list();
    }

    /** Body {@code {"settings": {"stt.provider": "mock", ...}}}; unknown keys → 422 UNKNOWN_SETTING. */
    @PutMapping
    public List<SettingResponse> update(@AuthenticationPrincipal User currentUser, @RequestBody UpdateSettingsRequest request) {
        return settingsService.update(request, currentUser.getUserId());
    }
}
