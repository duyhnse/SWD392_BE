package swd392.group6.AIVES.interview;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only SQL implementation of {@link InterviewApi}; the interview owner may replace it (keep one bean). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcInterviewApi implements InterviewApi {

    private final JdbcTemplate jdbc;

    @Override
    public List<ThreadInfo> getThreads(UUID sessionId) {
        Map<UUID, ThreadInfo> threads = new LinkedHashMap<>();
        jdbc.query("select session_question_id, question_id, order_no, status from session_questions where session_id = ? order by order_no",
                rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    threads.put(id, new ThreadInfo(id, rs.getObject(2, UUID.class), rs.getInt(3), rs.getString(4), new ArrayList<>()));
                }, sessionId);
        jdbc.query("""
                select turn_id, session_question_id, turn_type, followup_index, question_text, student_transcript, status,
                       response_duration_sec, asked_at, followup_decision, answer_audio_key
                from exam_turns where session_id = ? order by turn_order""",
                rs -> {
                    ThreadInfo thread = threads.get(rs.getObject(2, UUID.class));
                    if (thread != null) {
                        thread.turns().add(new TurnInfo(rs.getObject(1, UUID.class), rs.getString(3), rs.getInt(4), rs.getString(5),
                                rs.getString(6), rs.getString(7), (Integer) rs.getObject(8),
                                rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant(), rs.getString(10), rs.getString(11)));
                    }
                }, sessionId);
        return List.copyOf(threads.values());
    }
}
