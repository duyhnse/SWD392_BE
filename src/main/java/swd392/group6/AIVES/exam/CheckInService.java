package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamDtos.CheckInRequest;
import swd392.group6.AIVES.exam.ExamDtos.MyExam;
import swd392.group6.AIVES.questionbank.QuestionBankApi;
import swd392.group6.AIVES.questionbank.QuestionBankApi.QuestionSnapshot;
import swd392.group6.AIVES.questionbank.QuestionBankApi.RubricSnapshot;
import swd392.group6.AIVES.user.User;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Check-in = start of an attempt (D48): the student's questions are drawn now from the đề thi rows, copied into
 * {@code attempt_questions} together with everything grading needs (D49), and locked in the question bank.
 */
@Service
@RequiredArgsConstructor
@Transactional
class CheckInService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SecureRandom SEEDS = new SecureRandom();

    private final ExamTemplateRepository templates;
    private final ExamTemplateItemRepository items;
    private final VivaExamRepository exams;
    private final ExamQueries queries;
    private final ExamStatusRefresher refresher;
    private final MyExamService myExams;
    private final QuestionBankApi questionBank;
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    MyExam checkIn(UUID examId, CheckInRequest request, User student) {
        refresher.refreshDue();
        MapSqlParameterSource p = ExamQueries.params(examId).addValue("s", student.getUserId());
        // Serialises check-ins of one buổi thi so the usage counts that spread the questions are consistent.
        List<UUID> locked = jdbc.queryForList("select viva_exam_id from viva_exams where viva_exam_id = :e for update", p, UUID.class);
        Boolean onRoster = jdbc.queryForObject(
                "select exists(select 1 from viva_exam_students where viva_exam_id = :e and student_id = :s)", p, Boolean.class);
        if (locked.isEmpty() || !Boolean.TRUE.equals(onRoster)) {
            throw ApiException.notFound("VIVA_EXAM_NOT_FOUND", "Viva exam not found");
        }
        VivaExam exam = exams.findById(examId).orElseThrow();
        Instant now = clock.instant();

        Optional<String> existing = jdbc.queryForList(
                "select status from exam_attempts where viva_exam_id = :e and student_id = :s", p, String.class).stream().findFirst();
        if (existing.isPresent()) {
            if (existing.get().equals("IN_PROGRESS") || existing.get().equals("INTERRUPTED")) {
                return myExams.get(student, examId); // repeated click / retry after a network error
            }
            throw ApiException.conflict("ATTEMPT_ALREADY_USED", "This exam allows one attempt and it was already taken");
        }
        if (exam.getStatus() != ExamStatus.OPEN || now.isBefore(exam.getCheckinOpensAt()) || now.isAfter(exam.getCheckinClosesAt())) {
            throw ApiException.conflict("CHECKIN_NOT_OPEN", "Check-in for this exam is not open");
        }
        if (request == null || !request.consentRecording()) {
            throw ApiException.unprocessable("CONSENT_REQUIRED", "Accept the recording notice before starting");
        }

        ExamTemplate template = templates.findById(exam.getTemplateId()).orElseThrow();
        List<ExamTemplateItem> rows = items.findByTemplateIdOrderBySortOrder(template.getId());
        long seed = SEEDS.nextLong();
        QuestionSelector.Draw draw = QuestionSelector.draw(queries.candidates(template), ExamQueries.rows(rows), Set.of(),
                usage(examId), seed);
        if (draw.failed()) {
            throw new PoolTooSmallException(draw.shortages());
        }

        List<UUID> questionIds = draw.picks().stream().map(QuestionSelector.Pick::questionId).toList();
        Map<UUID, QuestionSnapshot> snapshots = questionBank.snapshot(questionIds);
        UUID attemptId = UUID.randomUUID();
        int duration = 0;
        List<MapSqlParameterSource> questionRows = new ArrayList<>();
        Set<UUID> rubricIds = new LinkedHashSet<>();
        int orderNo = 1;
        for (QuestionSelector.Pick pick : draw.picks()) {
            ExamTemplateItem row = rows.get(pick.rowIndex());
            QuestionSnapshot q = snapshots.get(pick.questionId());
            RubricSnapshot rubric = rubricOf(q);
            rubricIds.add(rubric.rubricId());
            duration += row.getSecondsPerQuestion();
            questionRows.add(new MapSqlParameterSource("id", UUID.randomUUID()).addValue("a", attemptId)
                    .addValue("q", q.questionId()).addValue("o", orderNo++).addValue("item", row.getId())
                    .addValue("topic", q.topicId()).addValue("topicName", q.topicName())
                    .addValue("bloom", q.bloomLevel() == null ? null : q.bloomLevel().name())
                    .addValue("lang", q.language().name()).addValue("content", q.content())
                    .addValue("ref", q.referenceAnswer()).addValue("ver", q.version())
                    .addValue("rubric", JSON.writeValueAsString(rubric)).addValue("budget", row.getSecondsPerQuestion()));
        }

        jdbc.update("""
                insert into exam_attempts (attempt_id, viva_exam_id, course_id, student_id, examiner_id, status, started_at,
                                           deadline_at, consent_recorded_at, selection_seed, client_info, created_at)
                values (:id, :e, :c, :s, :x, 'IN_PROGRESS', :now, :deadline, :now, :seed, :client, :now)""",
                new MapSqlParameterSource("id", attemptId).addValue("e", examId).addValue("c", exam.getCourseId())
                        .addValue("s", student.getUserId()).addValue("x", exam.getExaminerId())
                        .addValue("now", Timestamp.from(now)).addValue("deadline", Timestamp.from(now.plusSeconds(duration)))
                        .addValue("seed", seed).addValue("client", request.clientInfo()));
        jdbc.batchUpdate("""
                insert into attempt_questions (attempt_question_id, attempt_id, question_id, order_no, status, template_item_id,
                                               topic_id, topic_name, bloom_level, language, content,
                                               reference_answer, question_version, rubric_snapshot, time_budget_sec)
                values (:id, :a, :q, :o, 'PENDING', :item, :topic, :topicName, :bloom, :lang, :content, :ref, :ver,
                        cast(:rubric as jsonb), :budget)""", questionRows.toArray(MapSqlParameterSource[]::new));
        questionBank.lockForExam(questionIds, rubricIds);
        events.publishEvent(new AttemptStartedEvent(attemptId, examId, student.getUserId(), now, seed));
        return myExams.get(student, examId);
    }

    /** A question is graded with its own rubric (D57). */
    private static RubricSnapshot rubricOf(QuestionSnapshot q) {
        RubricSnapshot rubric = q.rubric();
        if (rubric == null || rubric.criteria().isEmpty()) {
            throw ApiException.unprocessable("RUBRIC_MISSING", "Question " + q.questionId() + " has no usable rubric");
        }
        return rubric;
    }

    private Map<UUID, Integer> usage(UUID examId) {
        Map<UUID, Integer> usage = new HashMap<>();
        jdbc.query("""
                        select q.question_id, count(*) from attempt_questions q join exam_attempts a on a.attempt_id = q.attempt_id
                        where a.viva_exam_id = :e and q.status <> 'VOIDED' group by q.question_id""",
                ExamQueries.params(examId), rs -> {
                    usage.put(rs.getObject(1, UUID.class), rs.getInt(2));
                });
        return usage;
    }
}
