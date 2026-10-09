package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;

import java.time.Clock;
import java.util.UUID;

/**
 * Link a Google account to an existing AIVES account, then sign in with it (14 §3.5, D25, D36).
 * Google never creates accounts.
 */
@Service
@RequiredArgsConstructor
class GoogleAccountService {

    private final GoogleIdentityVerifier verifier;
    private final UserRepository userRepository;
    private final UserService userService;
    private final Clock clock;

    @Transactional
    public UserResponseDTO link(UUID userId, String idToken) {
        GoogleIdentityVerifier.GoogleIdentity google = verifier.verify(idToken);
        if (!google.emailVerified()) {
            throw ApiException.unprocessable("GOOGLE_EMAIL_NOT_VERIFIED", "This Google account's email is not verified");
        }
        userRepository.findByGoogleSubject(google.subject())
                .filter(other -> !other.getUserId().equals(userId))
                .ifPresent(other -> {
                    throw ApiException.conflict("GOOGLE_ALREADY_LINKED", "This Google account is linked to another AIVES account");
                });
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        user.setGoogleSubject(google.subject());
        user.setGoogleEmail(google.email());
        user.setGoogleLinkedAt(clock.instant());
        return UserResponseDTO.fromEntity(userRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO login(String idToken) {
        GoogleIdentityVerifier.GoogleIdentity google = verifier.verify(idToken);
        User user = userRepository.findByGoogleSubject(google.subject())
                .orElseThrow(() -> ApiException.unauthorized("GOOGLE_NOT_LINKED",
                        "This Google account is not linked to any AIVES account"));
        if (!user.isActive()) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
        }
        return userService.issueToken(user);
    }
}
