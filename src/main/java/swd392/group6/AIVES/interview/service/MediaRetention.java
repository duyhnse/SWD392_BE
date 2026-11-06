package swd392.group6.AIVES.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.user.SettingsApi;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recording retention (15 §6, AIV-29): every night the camera / screen / audio chunks and the answer audio of attempts
 * that ended more than {@code retention.recording_days} days ago are deleted from storage. Transcripts, AI analysis,
 * grades and the event log stay. An attempt whose evaluation is not confirmed yet, or with an open dispute, keeps its
 * media (it is still evidence). Each purge is one {@code MEDIA_PURGED} event of the attempt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaRetention {

    static final String SETTING = "retention.recording_days";
    static final int DEFAULT_DAYS = 365;

    private final JdbcTemplate jdbc;
    private final StoragePort storage;
    private final SettingsApi settings;
    private final AttemptEventLog events;
    private final Clock clock;

    int days() {
        return settings.value(SETTING).map(v -> {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return DEFAULT_DAYS;
            }
        }).filter(d -> d >= 30).orElse(DEFAULT_DAYS);
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public int purge() {
        Instant now = clock.instant();
        Timestamp before = Timestamp.from(now.minus(Duration.ofDays(days())));
        List<UUID> attempts = jdbc.queryForList("""
                select a.attempt_id from exam_attempts a
                where a.ended_at < ? and a.status in ('COMPLETED', 'CANCELLED')
                  and (exists (select 1 from attempt_recordings r where r.attempt_id = a.attempt_id)
                       or exists (select 1 from exam_turns t where t.attempt_id = a.attempt_id and t.answer_audio_key is not null))
                  and (a.status = 'CANCELLED' or exists (select 1 from grade_evaluations e
                                                         where e.attempt_id = a.attempt_id and e.status = 'CONFIRMED'))
                  and not exists (select 1 from grade_disputes d join grade_evaluations e on e.evaluation_id = d.evaluation_id
                                  where e.attempt_id = a.attempt_id and d.status = 'OPEN')""", UUID.class, before);
        for (UUID attemptId : attempts) {
            List<String> keys = jdbc.queryForList("select storage_key from attempt_recordings where attempt_id = ?", String.class, attemptId);
            List<String> answers = jdbc.queryForList(
                    "select answer_audio_key from exam_turns where attempt_id = ? and answer_audio_key is not null", String.class, attemptId);
            keys.forEach(this::delete);
            answers.forEach(this::delete);
            jdbc.update("delete from attempt_recordings where attempt_id = ?", attemptId);
            jdbc.update("update exam_turns set answer_audio_key = null where attempt_id = ?", attemptId);
            events.log(attemptId, null, "MEDIA_PURGED", null,
                    Map.of("recordingChunks", keys.size(), "answerAudio", answers.size(), "retentionDays", days()), now);
        }
        if (!attempts.isEmpty()) {
            log.info("Retention: media of {} attempt(s) older than {} days deleted", attempts.size(), days());
        }
        return attempts.size();
    }

    private void delete(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) { // already gone: nothing to keep
            log.debug("Retention: {} not deleted: {}", key, e.getMessage());
        }
    }
}
