package swd392.group6.AIVES.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import swd392.group6.AIVES.user.UserApi.NewAccount;

import java.io.IOException;
import java.util.UUID;

/** Account administration, ADMIN only (14_AUTH_AND_ACCOUNTS.md §3.4, §5). */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
class AdminUserController {

    private static final long MAX_IMPORT_BYTES = 2 * 1024 * 1024;

    private final UserRepository userRepository;
    private final AccountProvisioningService provisioning;
    private final UserCsvImporter importer;

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<UserResponseDTO> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Role role,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        String query = q == null || q.isBlank() ? null : PasswordRules.normalize(q);
        var result = userRepository.search(query, role == null ? null : role.getId(),
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("username")));
        return PageResponse.of(result, UserResponseDTO::fromEntity);
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody CreateUserRequest request) {
        User user = provisioning.create(new NewAccount(request.username(), request.fullName(), request.email(),
                request.studentCode(), request.role()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDTO.fromEntity(user));
    }

    @PatchMapping("/{id}")
    @Transactional
    public UserResponseDTO update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = PasswordRules.normalize(request.email());
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "Email is already used by another account");
            }
            user.setEmail(email);
        }
        if (request.studentCode() != null) {
            String code = request.studentCode().isBlank() ? null : request.studentCode().trim().toUpperCase();
            if (code != null && !code.equals(user.getStudentCode()) && userRepository.existsByStudentCode(code)) {
                throw ApiException.conflict("STUDENT_CODE_ALREADY_EXISTS", "Student code is already used");
            }
            user.setStudentCode(code);
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        return UserResponseDTO.fromEntity(user);
    }

    @PostMapping(path = "/import", consumes = "multipart/form-data")
    public ImportReport importUsers(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_IMPORT_BYTES) {
            throw ApiException.unprocessable("IMPORT_FILE_INVALID", "Upload a CSV file of at most 2 MB");
        }
        return importer.importCsv(file.getInputStream(), true);
    }
}
