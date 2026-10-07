package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.mail.MailMessage;
import swd392.group6.AIVES.mail.MailPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Forgot password and first-time activation (14_AUTH_AND_ACCOUNTS.md §3.2, BR-A1..A6). */
@Slf4j
@Service
@RequiredArgsConstructor
class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResetRateLimiter rateLimiter;
    private final MailPort mailPort;
    private final Clock clock;

    @Value("${application.frontend-url}")
    private String frontendUrl;

    /** Always completes silently: the caller learns nothing about whether the account exists (BR-A1). */
    @Transactional
    public void request(PasswordResetRequest request, String clientIp) {
        String username = PasswordRules.normalize(request.username());
        String email = PasswordRules.normalize(request.email());
        if (!rateLimiter.tryAcquire(username, clientIp)) {
            log.info("Password reset rate limit hit for username={} ip={}", username, clientIp);
            return;
        }
        userRepository.findByUsername(username)
                .filter(User::isActive)
                .filter(user -> user.getEmail().equals(email))
                .ifPresent(user -> issueAndSend(user, clientIp));
    }

    @Transactional
    public void confirm(PasswordResetConfirm request) {
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(request.token()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(PasswordResetService::invalidToken);
        User user = userRepository.findById(token.getUserId())
                .filter(User::isActive)
                .orElseThrow(PasswordResetService::invalidToken);

        user.setHashedPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(now);
        tokenRepository.invalidateAllForUser(user.getUserId(), now);
    }

    private void issueAndSend(User user, String clientIp) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokenRepository.save(new PasswordResetToken(user.getUserId(), sha256(token), clock.instant().plus(TOKEN_TTL), clientIp));

        String link = frontendUrl + "/reset-password?token=" + token;
        mailPort.send(new MailMessage(user.getEmail(), "AIVES – Đặt mật khẩu / Set your password", """
                Xin chào %s,

                Có yêu cầu đặt mật khẩu cho tài khoản AIVES "%s".
                Mở liên kết sau trong vòng 30 phút để đặt mật khẩu mới:
                %s

                Nếu bạn không yêu cầu, hãy bỏ qua email này — mật khẩu hiện tại vẫn giữ nguyên.

                ---
                Someone asked to set the password of AIVES account "%s".
                Open the link above within 30 minutes. If it wasn't you, ignore this email.
                """.formatted(user.getFullName(), user.getUsername(), link, user.getUsername())));
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "RESET_TOKEN_INVALID",
                "This link is invalid or has expired. Please request a new one.");
    }
}
