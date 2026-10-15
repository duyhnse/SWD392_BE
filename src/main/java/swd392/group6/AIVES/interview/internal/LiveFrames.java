package swd392.group6.AIVES.interview.internal;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The latest still image of each running attempt's camera and screen, for the examiner's live view (proctoring,
 * D65). Memory only and short-lived: the evidence is the recording (D50); a frame older than {@link #MAX_AGE} is gone.
 */
@Component
class LiveFrames {

    static final Duration MAX_AGE = Duration.ofSeconds(60);
    static final int MAX_BYTES = 400 * 1024;

    record Frame(byte[] data, String contentType, Instant at) {
    }

    private final Map<String, Frame> frames = new ConcurrentHashMap<>();

    void put(UUID attemptId, String kind, Frame frame) {
        frames.put(attemptId + "/" + kind, frame);
        if (frames.size() > 2_000) {
            evict(frame.at());
        }
    }

    Optional<Frame> get(UUID attemptId, String kind, Instant now) {
        Frame f = frames.get(attemptId + "/" + kind);
        return f == null || f.at().plus(MAX_AGE).isBefore(now) ? Optional.empty() : Optional.of(f);
    }

    void drop(UUID attemptId) {
        frames.keySet().removeIf(k -> k.startsWith(attemptId + "/"));
    }

    private void evict(Instant now) {
        frames.entrySet().removeIf(e -> e.getValue().at().plus(MAX_AGE).isBefore(now));
    }
}
