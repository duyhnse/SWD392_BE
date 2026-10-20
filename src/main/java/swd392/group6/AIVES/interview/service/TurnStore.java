package swd392.group6.AIVES.interview.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL on {@code exam_turns}, the interview module's own table. Callers hold the attempt lock. */
@Component
@RequiredArgsConstructor
public class TurnStore {

    static final String NO_ANSWER_TEXT = "[NO_ANSWER]";

    private final JdbcTemplate jdbc;

    record Turn(UUID turnId, UUID attemptId, UUID attemptQuestionId, UUID questionId, String type, int turnOrder,
                int followupIndex, String questionText, String questionAudioKey, Integer questionAudioMs, String language,
                String status, Instant askedAt, Instant answerSubmittedAt, String transcript, Integer processingMs) {

        boolean open() {
            return "ASKED".equals(status) || "PROCESSING".equals(status);
        }

        /** A turn the student really answered (anything but skipped / cancelled / never answered). */
        boolean answered() {
            return "ANSWERED".equals(status) || "NO_ANSWER".equals(status) || "TRANSCRIPTION_FAILED".equals(status);
        }
    }

    private static final String SELECT = """
            select turn_id, attempt_id, attempt_question_id, question_id, turn_type, turn_order, followup_index,
                   question_text, question_audio_key, question_audio_ms, language, status, asked_at, answer_submitted_at,
                   student_transcript, processing_ms
            from exam_turns""";

    private static final RowMapper<Turn> TURN = (rs, i) -> new Turn(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
            rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5), rs.getInt(6), rs.getInt(7),
            rs.getString(8), rs.getString(9), (Integer) rs.getObject(10), rs.getString(11), rs.getString(12),
            instant(rs.getTimestamp(13)), instant(rs.getTimestamp(14)), rs.getString(15), (Integer) rs.getObject(16));

    Optional<Turn> get(UUID turnId) {
        return jdbc.query(SELECT + " where turn_id = ?", TURN, turnId).stream().findFirst();
    }

    /** The one turn waiting for an answer or being processed (unique index {@code ux_exam_turns_one_open_turn}). */
    Optional<Turn> open(UUID attemptId) {
        return jdbc.query(SELECT + " where attempt_id = ? and status in ('ASKED', 'PROCESSING')", TURN, attemptId)
                .stream().findFirst();
    }

    List<Turn> thread(UUID attemptQuestionId) {
        return jdbc.query(SELECT + " where attempt_question_id = ? order by followup_index", TURN, attemptQuestionId);
    }

    List<Turn> all(UUID attemptId) {
        return jdbc.query(SELECT + " where attempt_id = ? order by turn_order", TURN, attemptId);
    }

    Optional<Turn> last(UUID attemptId) {
        return jdbc.query(SELECT + " where attempt_id = ? order by turn_order desc limit 1", TURN, attemptId)
                .stream().findFirst();
    }

    /** Inserts an ASKED turn and returns its id. {@code parentTurnId} null = MAIN. */
    /** Voice added after the turn was asked (D70); never replaces an existing one. */
    boolean setQuestionAudio(UUID turnId, QuestionAudio.Stored audio) {
        return jdbc.update("update exam_turns set question_audio_key = ?, question_audio_ms = ? where turn_id = ? and question_audio_key is null",
                audio.key(), audio.durationMs(), turnId) > 0;
    }

    UUID ask(UUID attemptId, UUID attemptQuestionId, UUID questionId, UUID parentTurnId, int followupIndex, String text,
             String language, QuestionAudio.Stored audio, Instant at) {
        UUID id = UUID.randomUUID();
        Integer next = jdbc.queryForObject("select coalesce(max(turn_order), 0) + 1 from exam_turns where attempt_id = ?",
                Integer.class, attemptId);
        jdbc.update("""
                        insert into exam_turns (turn_id, attempt_id, attempt_question_id, question_id, parent_turn_id, turn_type,
                                                turn_order, followup_index, question_text, question_audio_key, question_audio_ms,
                                                language, status, asked_at, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ASKED', ?, ?)""",
                id, attemptId, attemptQuestionId, questionId, parentTurnId, parentTurnId == null ? "MAIN" : "FOLLOW_UP",
                next, followupIndex, text, audio == null ? null : audio.key(), audio == null ? null : audio.durationMs(),
                language, ts(at), ts(at));
        return id;
    }

    void markProcessing(UUID turnId, Instant submittedAt, Integer durationSec, int countedMs, String audioKey) {
        jdbc.update("""
                        update exam_turns set status = 'PROCESSING', answer_submitted_at = ?, response_duration_sec = ?,
                                              answer_counted_ms = ?, answer_audio_key = ?
                        where turn_id = ? and status = 'ASKED'""",
                ts(submittedAt), durationSec, countedMs, audioKey, turnId);
    }

    /**
     * Stores the AI outcome. The status only moves on while the turn is still PROCESSING; when the examiner skipped or
     * ended meanwhile the transcript and analysis are still kept as evidence.
     */
    void saveResult(UUID turnId, String status, String transcript, String analysisJson, String decision, int processingMs) {
        jdbc.update("""
                        update exam_turns set status = case when status = 'PROCESSING' then ? else status end,
                               student_transcript = ?, ai_analysis = cast(? as jsonb), followup_decision = ?, processing_ms = ?
                        where turn_id = ?""",
                status, transcript, analysisJson, decision, processingMs, turnId);
    }

    /** No answer within the limit (EX-1) or the client reported silence. */
    void noAnswer(UUID turnId, Instant at, int countedMs) {
        jdbc.update("""
                        update exam_turns set status = 'NO_ANSWER', student_transcript = ?, followup_decision = 'NEXT_NO_ANSWER',
                               answer_submitted_at = ?, answer_counted_ms = ?
                        where turn_id = ? and status = 'ASKED'""",
                NO_ANSWER_TEXT, ts(at), countedMs, turnId);
    }

    /** Closes an open turn ({@code SKIPPED_BY_LECTURER}, {@code CANCELLED}). */
    void close(UUID turnId, String status) {
        jdbc.update("update exam_turns set status = ? where turn_id = ? and status in ('ASKED', 'PROCESSING')", status, turnId);
    }

    /** Asks the open turn again (reconnect, resume, stuck processing — D51, EX-9). The old answer is discarded. */
    void reask(UUID turnId, Instant at) {
        jdbc.update("""
                        update exam_turns set status = 'ASKED', asked_at = ?, answer_submitted_at = null,
                               response_duration_sec = null, answer_counted_ms = null, answer_audio_key = null
                        where turn_id = ? and status in ('ASKED', 'PROCESSING')""", ts(at), turnId);
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
