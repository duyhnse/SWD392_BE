package swd392.group6.AIVES.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.user.UserApi.NewAccount;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development-only self registration (14 §3.7, D39): anyone can create a LECTURER or STUDENT account with a
 * password, without email confirmation. Off unless {@code application.dev.self-registration-enabled=true}
 * (the demo server turns it on); never ADMIN; 10 accounts per IP per hour.
 */
@RestController
@RequestMapping("/api/v1/auth/dev-registration")
@RequiredArgsConstructor
class DevRegistrationController {

    static final int MAX_PER_IP_PER_HOUR = 10;

    private final AccountProvisioningService provisioning;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    @Value("${application.dev.self-registration-enabled:false}")
    private boolean enabled;

    record Status(boolean enabled) {
    }

    record DevRegistrationRequest(
            @NotBlank(message = "Username is required") @Size(max = 50) String username,
            @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Email format is invalid") @Size(max = 100) String email,
            @Size(max = 20) String studentCode, // required for STUDENT (checked by provisioning, D40)
            @NotNull(message = "Role is required") Role role,
            @NotBlank(message = "Password is required")
            @Size(min = PasswordRules.MIN_PASSWORD, max = PasswordRules.MAX_PASSWORD,
                  message = "Password must be between 8 and 100 characters") String password,
            @NotBlank(message = "Please repeat the password") String confirmPassword
    ) {
    }

    @GetMapping
    public Status status() {
        return new Status(enabled);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody DevRegistrationRequest request, HttpServletRequest http) {
        if (!enabled) {
            throw ApiException.notFound("DEV_REGISTRATION_DISABLED", "Self registration is disabled on this server");
        }
        if (request.role() == Role.ADMIN) {
            throw ApiException.unprocessable("ROLE_NOT_ALLOWED", "Administrator accounts cannot be self-registered");
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw ApiException.unprocessable("PASSWORD_MISMATCH", "The two passwords do not match");
        }
        if (!withinLimit(http.getRemoteAddr())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REGISTRATIONS",
                    "Too many accounts created from this address, try again later");
        }
        User user = provisioning.create(new NewAccount(request.username(), request.fullName(), request.email(),
                request.role() == Role.STUDENT ? request.studentCode() : null, request.role()));
        user.setHashedPassword(passwordEncoder.encode(request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDTO.fromEntity(userRepository.saveAndFlush(user)));
    }

    private boolean withinLimit(String ip) {
        Instant now = clock.instant();
        Deque<Instant> deque = hits.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                deque.pollFirst();
            }
            if (deque.size() >= MAX_PER_IP_PER_HOUR) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
