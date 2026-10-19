package swd392.group6.AIVES.interview.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.ai.SpeechToTextPort;
import swd392.group6.AIVES.ai.SpeechToTextRequest;
import swd392.group6.AIVES.common.Language;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Incremental speech-to-text (D74). While a student speaks, the browser sends a self-contained recording of every ~25 s;
 * each is transcribed here in the background. When the answer is submitted, the transcripts of parts 0..n-1 are the
 * prefix and the AI node only transcribes the last part — the turn no longer waits for minutes of audio. If any part
 * is missing or failed, the full recording is used as before.
 */
@Slf4j
@Component
class AnswerParts {

    static final int MAX_PARTS = 60;
    static final long MAX_PART_BYTES = 5L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final SpeechToTextPort stt;
    private final Clock clock;
    private final ExecutorService worker = Executors.newFixedThreadPool(4, Thread.ofVirtual().name("answer-part-", 0).factory());

    AnswerParts(JdbcTemplate jdbc, SpeechToTextPort stt, Clock clock) {
        this.jdbc = jdbc;
        this.stt = stt;
        this.clock = clock;
    }

    /** Stores the part once (a retried upload is ignored) and transcribes it in the background. */
    void accept(UUID turnId, int index, byte[] audio, String contentType, Language language, String hotwords) {
        int inserted = jdbc.update("""
                        insert into answer_parts (turn_id, part_index, size_bytes, created_at) values (?, ?, ?, ?)
                        on conflict do nothing""", turnId, index, audio.length, java.sql.Timestamp.from(clock.instant()));
        if (inserted == 0) {
            return;
        }
        worker.submit(() -> {
            try {
                String text = stt.transcribe(new SpeechToTextRequest(audio, contentType, language, hotwords)).text();
                jdbc.update("update answer_parts set status = 'DONE', transcript = ?, done_at = ? where turn_id = ? and part_index = ?",
                        text == null ? "" : text.strip(), java.sql.Timestamp.from(clock.instant()), turnId, index);
            } catch (RuntimeException e) {
                log.warn("Part {} of turn {} not transcribed: {}", index, turnId, e.getMessage());
                jdbc.update("update answer_parts set status = 'FAILED', error = ?, done_at = ? where turn_id = ? and part_index = ?",
                        String.valueOf(e.getMessage()), java.sql.Timestamp.from(clock.instant()), turnId, index);
            }
        });
    }

    /**
     * The transcripts of parts 0..count-1 joined, waiting up to {@code wait} for the last ones still running; empty when
     * one is missing or failed (then the caller uses the full recording).
     */
    Optional<String> prefix(UUID turnId, int count, Duration wait) {
        Instant until = Instant.now().plus(wait);
        while (true) {
            List<String[]> rows = jdbc.query("select status, transcript from answer_parts where turn_id = ? and part_index < ? order by part_index",
                    (rs, i) -> new String[] {rs.getString(1), rs.getString(2)}, turnId, count);
            if (rows.size() < count || rows.stream().anyMatch(r -> "FAILED".equals(r[0]))) {
                return Optional.empty();
            }
            if (rows.stream().allMatch(r -> "DONE".equals(r[0]))) {
                return Optional.of(String.join(" ", rows.stream().map(r -> r[1]).filter(s -> !s.isBlank()).toList()));
            }
            if (Instant.now().isAfter(until)) {
                return Optional.empty();
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }
}
