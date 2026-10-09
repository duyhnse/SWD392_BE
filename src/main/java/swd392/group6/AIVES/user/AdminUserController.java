package swd392.group6.AIVES.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.user.AdminUserDtos.CreateUserRequest;
import swd392.group6.AIVES.user.AdminUserDtos.ImportReport;
import swd392.group6.AIVES.user.AdminUserDtos.UpdateUserRequest;
import swd392.group6.AIVES.user.AdminUserDtos.UserDetail;
import swd392.group6.AIVES.user.UserApi.NewAccount;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Account administration, ADMIN only (14_AUTH_AND_ACCOUNTS.md §3.4, §5). */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
class AdminUserController {

    private static final long MAX_IMPORT_BYTES = 2 * 1024 * 1024;

    private final AccountProvisioningService provisioning;
    private final UserCsvImporter importer;
    private final AdminUserService adminUsers;
    private final AvatarService avatarService;
    private final PasswordResetService passwordReset;

    @GetMapping
    public PageResponse<UserResponseDTO> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Role role,
                                              @RequestParam(required = false) Boolean active,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return adminUsers.list(q, role, active, page, size);
    }

    /** Profile + role details: assigned courses (lecturer) or number of lượt thi (student). */
    @GetMapping("/{id}")
    public UserDetail get(@PathVariable UUID id) {
        return adminUsers.detail(id);
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody CreateUserRequest request) {
        User user = provisioning.create(new NewAccount(request.username(), request.fullName(), request.email(),
                request.studentCode(), request.role()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDTO.fromEntity(user));
    }

    @PatchMapping("/{id}")
    public UserResponseDTO update(@AuthenticationPrincipal User currentUser, @PathVariable UUID id,
                                  @Valid @RequestBody UpdateUserRequest request) {
        return adminUsers.update(id, request, currentUser);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        adminUsers.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /** Mails the user a one-time reset link (same mail as Forgot password). */
    @PostMapping("/{id}/password-reset")
    public ResponseEntity<Void> sendPasswordReset(@PathVariable UUID id, HttpServletRequest http) {
        passwordReset.sendResetLinkFor(adminUsers.load(id), http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @PutMapping(path = "/{id}/avatar", consumes = "multipart/form-data")
    public Map<String, String> uploadAvatar(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return Map.of("avatarUrl", avatarService.replace(id, file));
    }

    @DeleteMapping("/{id}/avatar")
    public ResponseEntity<Void> deleteAvatar(@PathVariable UUID id) {
        avatarService.remove(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/import", consumes = "multipart/form-data")
    public ImportReport importUsers(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_IMPORT_BYTES) {
            throw ApiException.unprocessable("IMPORT_FILE_INVALID", "Upload a CSV file of at most 2 MB");
        }
        return importer.importCsv(file.getInputStream(), true);
    }
}
