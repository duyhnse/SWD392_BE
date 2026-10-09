package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.security.JwtService;
import swd392.group6.AIVES.security.SessionValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One active login per account (14 §3.6, D38). A login closes the account's other sessions; if one of them was
 * in use recently on another device, the login is refused with 409 SESSION_ACTIVE_ELSEWHERE until the user
 * confirms ({@code force}). Every authenticated request checks that its session is still open.
 */
@Service
@RequiredArgsConstructor
class SessionService implements SessionValidator {

    /** A session seen within this window counts as "in use" on the other device. */
    static final Duration ACTIVE_WINDOW = Duration.ofMinutes(30);
    /** last_seen_at is refreshed at most this often (avoids a write per request). */
    static final Duration TOUCH_INTERVAL = Duration.ofSeconds(60);

    private final UserSessionRepository sessions;
    private final JwtService jwtService;
    private final Clock clock;

    @Transactional
    public UUID open(User user, LoginContext ctx) {
        Instant now = clock.instant();
        List<UserSession> open = sessions.findOpen(user.getUserId(), now);
        Optional<UserSession> inUseElsewhere = open.stream()
                .filter(s -> !sameDevice(s, ctx) && s.getLastSeenAt().isAfter(now.minus(ACTIVE_WINDOW)))
                .max(Comparator.comparing(UserSession::getLastSeenAt));
        if (inUseElsewhere.isPresent() && !ctx.force()) {
            UserSession other = inUseElsewhere.get();
            throw ApiException.conflict("SESSION_ACTIVE_ELSEWHERE",
                            "This account is signed in on another device. Sign that device out to continue here.")
                    .with("device", describeDevice(other.getUserAgent()))
                    .with("lastSeenAt", other.getLastSeenAt().toString());
        }
        sessions.revokeAll(user.getUserId(), now, "REPLACED");
        UserSession session = new UserSession(user.getUserId(), ctx, now, now.plusMillis(jwtService.getExpirationTime()));
        return sessions.save(session).getSessionId();
    }

    /** "Đăng xuất": closes the caller's session so another device can sign in without a prompt. */
    @Transactional
    public void close(String sessionId) {
        parse(sessionId).flatMap(sessions::findById).filter(s -> s.getRevokedAt() == null).ifPresent(s -> {
            s.setRevokedAt(clock.instant());
            s.setRevokeReason("LOGOUT");
        });
    }

    @Transactional
    public void closeAll(UUID userId, String reason) {
        sessions.revokeAll(userId, clock.instant(), reason);
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> find(String sessionId) {
        return parse(sessionId).flatMap(sessions::findById);
    }

    @Override
    @Transactional
    public boolean isSessionOpen(String sessionId, UserDetails principal) {
        if (!(principal instanceof User user)) {
            return false;
        }
        Instant now = clock.instant();
        Optional<UserSession> session = parse(sessionId).flatMap(sessions::findById)
                .filter(s -> s.getUserId().equals(user.getUserId()) && s.isOpen(now));
        session.filter(s -> s.getLastSeenAt().isBefore(now.minus(TOUCH_INTERVAL)))
                .ifPresent(s -> sessions.touch(s.getSessionId(), now));
        return session.isPresent();
    }

    private static boolean sameDevice(UserSession session, LoginContext ctx) {
        return ctx.deviceId() != null && ctx.deviceId().equals(session.getDeviceId());
    }

    private static Optional<UUID> parse(String sessionId) {
        try {
            return Optional.ofNullable(sessionId).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** "Chrome · Windows" from a User-Agent (rough, for the confirmation dialog only). */
    static String describeDevice(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Unknown device";
        }
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : "Browser";
        String os = ua.contains("Windows") ? "Windows" : ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Mac OS X") ? "macOS"
                : ua.contains("Linux") ? "Linux" : null;
        return os == null ? browser : browser + " · " + os;
    }
}
