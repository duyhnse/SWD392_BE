package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.security.JwtService;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Login, profile and own-password management (14_AUTH_AND_ACCOUNTS.md §3.1, §3.3). */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final Clock clock;
    private final AvatarService avatarService;

    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginRequestDTO request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(PasswordRules.normalize(request.getUsername()), request.getPassword()));
        } catch (AuthenticationException e) {
            // Same answer for unknown username, wrong password and inactive account (AC-A1).
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
        }
        return toAuthResponse((User) authentication.getPrincipal());
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getUserProfile(UUID id) {
        return userRepository.findById(id)
                .map(UserResponseDTO::fromEntity)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found with id: " + id));
    }

    /** Other devices are logged out (D24); the caller gets a fresh token so it stays signed in. */
    @Transactional
    public AuthResponseDTO changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getHashedPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INCORRECT", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getHashedPassword())) {
            throw ApiException.unprocessable("PASSWORD_UNCHANGED", "The new password must be different from the current one");
        }
        user.setHashedPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(clock.instant());
        return toAuthResponse(user);
    }

    /** Self-service profile edit: only the UI language; name, email and code come from the university (15 §5.1). */
    @Transactional
    public UserResponseDTO updateOwnProfile(UUID userId, UpdateProfileRequest request) {
        User user = load(userId);
        if (request.preferredLanguage() != null) {
            user.setPreferredLanguage(request.preferredLanguage());
        }
        return UserResponseDTO.fromEntity(userRepository.saveAndFlush(user));
    }

    /** Unlink Google (D34): clears subject, email and link time. Idempotent. */
    @Transactional
    public UserResponseDTO unlinkGoogle(UUID userId) {
        avatarService.removeIfFromGoogle(userId); // D37: a picture adopted from Google leaves with it
        User user = load(userId);
        user.setGoogleSubject(null);
        user.setGoogleEmail(null);
        user.setGoogleLinkedAt(null);
        return UserResponseDTO.fromEntity(userRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO currentProfile(UUID userId) {
        return UserResponseDTO.fromEntity(load(userId));
    }

    private User load(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    /** Token for an already authenticated user (e.g. Google sign-in). */
    AuthResponseDTO issueToken(User user) {
        return toAuthResponse(user);
    }

    private AuthResponseDTO toAuthResponse(User user) {
        return AuthResponseDTO.builder()
                .token(jwtService.generateToken(user.getUsername(), tokenClaims(user)))
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .user(UserResponseDTO.fromEntity(user))
                .build();
    }

    private static Map<String, Object> tokenClaims(User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.getUserId().toString());
        claims.put("role", user.getRole().name());
        claims.put("roleId", user.getRoleId());
        claims.put("fullName", user.getFullName());
        return claims;
    }
}
