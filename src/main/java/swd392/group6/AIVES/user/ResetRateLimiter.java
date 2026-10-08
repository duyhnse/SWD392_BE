package swd392.group6.AIVES.user;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sliding window for reset requests (BR-A3). Good enough for one backend instance. */
@Component
class ResetRateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_PER_USERNAME = 3;
    static final int MAX_PER_IP = 10;

    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    ResetRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records the attempt and tells whether it is still within both limits. */
    boolean tryAcquire(String username, String ip) {
        boolean userOk = record("u:" + username, MAX_PER_USERNAME);
        boolean ipOk = ip == null || record("ip:" + ip, MAX_PER_IP);
        return userOk && ipOk;
    }

    private boolean record(String key, int max) {
        Instant now = clock.instant();
        Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(now.minus(WINDOW))) {
                deque.pollFirst();
            }
            deque.addLast(now);
            return deque.size() <= max;
        }
    }
}
