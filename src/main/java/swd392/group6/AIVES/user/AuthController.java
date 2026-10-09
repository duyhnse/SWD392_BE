package swd392.group6.AIVES.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public auth endpoints. There is no self sign-up (D21): accounts are provisioned by admins/lecturers. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final GoogleAccountService googleAccountService;
    private final SessionService sessionService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest http) {
        return ResponseEntity.ok(userService.login(request, LoginContext.of(http, request.getDeviceId(), request.getForce())));
    }

    /** Sign in with a Google account that was linked beforehand; never creates accounts (14 §3.5). */
    @PostMapping("/google")
    public ResponseEntity<AuthResponseDTO> loginWithGoogle(@Valid @RequestBody GoogleTokenRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(googleAccountService.login(request.idToken(), LoginContext.of(http, request.deviceId(), request.force())));
    }

    /** Closes this device's session (D38), so another device can sign in without a prompt. Always 204. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        Object sessionId = http.getAttribute(swd392.group6.AIVES.security.AuthAttributes.SESSION_ID);
        if (sessionId != null) {
            sessionService.close(sessionId.toString());
        }
        return ResponseEntity.noContent().build();
    }

    /** Always 202 with no body, whether or not the account exists (BR-A1). */
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                     HttpServletRequest http) {
        passwordResetService.request(request, http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirm request) {
        passwordResetService.confirm(request);
        return ResponseEntity.noContent().build();
    }
}
