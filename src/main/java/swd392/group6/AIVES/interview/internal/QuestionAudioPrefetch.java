package swd392.group6.AIVES.interview.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.exam.AttemptStartedEvent;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Synthesises every drawn main question right after check-in (16 §3: main questions are never voiced inside a turn), so
 * moving to the next main question costs no TTS time. Background, one attempt after another; a failure only means the
 * turn synthesises (or the browser speaks) that question as before. A follow-up whose voice did not come with the AI
 * answer is shown at once and voiced here; the browser waits a few seconds for it (D70).
 */
@Slf4j
@Component
class QuestionAudioPrefetch {

    private final QuestionAudio audio;
    private final JdbcTemplate jdbc;
    private final TurnStore turns;
    private final AttemptEventLog events;
    private final java.time.Clock clock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("tts-prefetch").factory());

    QuestionAudioPrefetch(QuestionAudio audio, JdbcTemplate jdbc, TurnStore turns, AttemptEventLog events,
                          java.time.Clock clock) {
        this.audio = audio;
        this.jdbc = jdbc;
        this.turns = turns;
        this.events = events;
        this.clock = clock;
    }

    /** After the current transaction commits (the turn row must exist): voice the follow-up and attach it. */
    void followUp(UUID attemptId, UUID turnId, String text, Language language) {
        Runnable job = () -> worker.submit(() -> {
            try {
                audio.forText(text, language).ifPresentOrElse(a -> turns.setQuestionAudio(turnId, a),
                        () -> events.log(attemptId, turnId, "TTS_FAILED", null, Map.of("turnType", "FOLLOW_UP"), clock.instant()));
            } catch (RuntimeException ex) {
                log.warn("Follow-up voice for turn {} failed: {}", turnId, ex.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    job.run();
                }
            });
        } else {
            job.run();
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(AttemptStartedEvent e) {
        worker.submit(() -> prefetch(e));
    }

    private void prefetch(AttemptStartedEvent e) {
        try {
            jdbc.query("""
                            select content, language from attempt_questions
                            where attempt_id = ? and status <> 'VOIDED' order by order_no""",
                    rs -> {
                        audio.forText(rs.getString(1), Language.valueOf(rs.getString(2)));
                    }, e.attemptId());
        } catch (RuntimeException ex) {
            log.warn("Question audio prefetch for attempt {} failed: {}", e.attemptId(), ex.getMessage());
        }
    }
}
