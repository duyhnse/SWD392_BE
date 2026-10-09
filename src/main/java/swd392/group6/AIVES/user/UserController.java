package swd392.group6.AIVES.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;

    /** Profile of the currently authenticated user. */
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> me(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(UserResponseDTO.fromEntity(currentUser));
    }

    @PatchMapping("/me")
    public UserResponseDTO updateMe(@AuthenticationPrincipal User currentUser, @RequestBody UpdateProfileRequest request) {
        return userService.updateOwnProfile(currentUser.getUserId(), request);
    }

    /** Multipart field {@code file}: PNG/JPEG/WebP, at most 2 MB (D33). */
    @PutMapping(path = "/me/avatar", consumes = "multipart/form-data")
    public Map<String, String> uploadAvatar(@AuthenticationPrincipal User currentUser,
                                            @RequestPart("file") MultipartFile file) {
        return Map.of("avatarUrl", avatarService.replace(currentUser.getUserId(), file));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> deleteAvatar(@AuthenticationPrincipal User currentUser) {
        avatarService.remove(currentUser.getUserId());
        return ResponseEntity.noContent().build();
    }

    /** Unlink the Google account (D34). Linking arrives with the OAuth flow (P1). */
    @DeleteMapping("/me/google")
    public UserResponseDTO unlinkGoogle(@AuthenticationPrincipal User currentUser) {
        return userService.unlinkGoogle(currentUser.getUserId());
    }

    @PutMapping("/me/password")
    public ResponseEntity<AuthResponseDTO> changePassword(@AuthenticationPrincipal User currentUser,
                                                          @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(userService.changePassword(currentUser.getUserId(), request));
    }

    /** A user can read their own profile; only an ADMIN can read anybody else's. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.userId")
    public ResponseEntity<UserResponseDTO> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserProfile(id));
    }
}
