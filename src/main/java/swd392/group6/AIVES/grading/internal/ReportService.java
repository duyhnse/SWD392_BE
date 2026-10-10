package swd392.group6.AIVES.grading.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.exam.ExamApi.ExamInfo;
import swd392.group6.AIVES.grading.internal.GradingDtos.Bin;
import swd392.group6.AIVES.grading.internal.GradingDtos.QuestionStats;
import swd392.group6.AIVES.grading.internal.GradingDtos.ReportDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.ScoreStats;
import swd392.group6.AIVES.user.User;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** FG6 class statistics and the score sheet export of a buổi thi (15 §5.5, 08 P3). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ReportService {

    static final BigDecimal GOOD_ANSWER = BigDecimal.valueOf(7);
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final List<String> STAGES =
            List.of("UPCOMING", "AVAILABLE", "IN_PROGRESS", "INTERRUPTED", "COMPLETED", "MISSED", "CANCELLED");
    private static final List<String> STATUSES = List.of("IN_PROGRESS", "INTERRUPTED", "COMPLETED", "CANCELLED");

    private final GradingSupport support;
    private final GradeEvaluationRepository evaluations;
    private final QuestionGradeRepository grades;
    private final Clock clock;

    public ReportDto report(UUID vivaExamId, User user) {
        ExamInfo exam = support.requireExamRead(vivaExamId, user);
        List<GradingSupport.AttemptRow> attempts = support.attemptsOfExam(vivaExamId);
        List<GradingSupport.RosterRow> roster = support.rosterOfExam(vivaExamId);
        Instant now = clock.instant();

        Map<String, Integer> byStatus = zeroCounts(STATUSES);
        for (GradingSupport.AttemptRow s : attempts) {
            byStatus.merge(s.status(), 1, Integer::sum);
        }
        Map<String, Integer> byStage = zeroCounts(STAGES);
        for (GradingSupport.RosterRow r : roster) {
            byStage.merge(stage(r.attemptStatus(), exam, now), 1, Integer::sum);
        }

        List<GradeEvaluation> evals = evaluations.findByAttemptIdIn(
                attempts.stream().map(GradingSupport.AttemptRow::attemptId).toList());
        List<BigDecimal> totals = evals.stream().filter(e -> e.getStatus() == EvaluationStatus.CONFIRMED)
                .map(GradeEvaluation::getFinalTotalScore).filter(Objects::nonNull).toList();
        ScoreStats stats = new ScoreStats(totals.size(), ScoreCalculator.mean(totals), ScoreCalculator.median(totals),
                totals.stream().min(Comparator.naturalOrder()).map(v -> v.setScale(2, RoundingMode.HALF_UP)).orElse(null),
                totals.stream().max(Comparator.naturalOrder()).map(v -> v.setScale(2, RoundingMode.HALF_UP)).orElse(null));
        int[] bins = new int[10];
        for (BigDecimal total : totals) {
            bins[Math.max(0, Math.min(9, total.intValue()))]++;
        }
        List<Bin> distribution = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            distribution.add(new Bin(i, i + 1, bins[i]));
        }

        List<QuestionStats> questions = questionStats(vivaExamId);
        List<QuestionStats> hardest = questions.stream().filter(q -> q.gradedCount() > 0)
                .sorted(Comparator.comparing(QuestionStats::averageFinalScore).thenComparing(q -> q.questionId().toString()))
                .limit(5).toList();
        Integer passed = exam.passScore() == null ? null
                : (int) totals.stream().filter(t -> t.compareTo(exam.passScore()) >= 0).count();
        return new ReportDto(vivaExamId, exam.title(), roster.size(), attempts.size(), byStatus, byStage, evals.size(),
                (int) evals.stream().filter(e -> e.getStatus() == EvaluationStatus.CONFIRMED).count(), stats,
                exam.passScore(), passed, distribution, questions, hardest);
    }

    /**
     * Per main question of the buổi thi: times actually asked (thread has a turn, completed lượt thi), and over the
     * CONFIRMED evaluations the average thread final score and the share of finals ≥ 7.
     */
    private List<QuestionStats> questionStats(UUID vivaExamId) {
        record Row(UUID questionId, String content, boolean asked, BigDecimal finalScore) {
        }
        List<Row> rows = support.jdbc().query("""
                        select sq.question_id, sq.content,
                               exists (select 1 from exam_turns t where t.attempt_question_id = sq.attempt_question_id) as asked,
                               case when ge.status = 'CONFIRMED' then qg.final_score end as final_score
                        from exam_attempts s
                        join attempt_questions sq on sq.attempt_id = s.attempt_id
                        left join grade_evaluations ge on ge.attempt_id = s.attempt_id
                        left join question_grades qg on qg.evaluation_id = ge.evaluation_id
                                                     and qg.attempt_question_id = sq.attempt_question_id
                        where s.viva_exam_id = :exam and s.status = 'COMPLETED' and sq.status <> 'VOIDED'""",
                new MapSqlParameterSource("exam", vivaExamId),
                (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3), rs.getBigDecimal(4)));
        Map<UUID, List<Row>> byQuestion = rows.stream()
                .collect(Collectors.groupingBy(Row::questionId, LinkedHashMap::new, Collectors.toList()));
        List<QuestionStats> result = new ArrayList<>();
        byQuestion.forEach((questionId, list) -> {
            List<BigDecimal> finals = list.stream().filter(Row::asked).map(Row::finalScore).filter(Objects::nonNull).toList();
            long good = finals.stream().filter(f -> f.compareTo(GOOD_ANSWER) >= 0).count();
            BigDecimal rate = finals.isEmpty() ? null
                    : BigDecimal.valueOf(good).divide(BigDecimal.valueOf(finals.size()), 4, RoundingMode.HALF_UP);
            String content = list.getFirst().content(); // wording as asked (snapshot, D49)
            result.add(new QuestionStats(questionId, content, (int) list.stream().filter(Row::asked).count(),
                    finals.size(), ScoreCalculator.mean(finals), rate));
        });
        result.sort(Comparator.comparing(QuestionStats::timesAsked).reversed()
                .thenComparing(q -> q.questionId().toString()));
        return result;
    }

    /** Score sheet: STT, MSSV, Họ tên, Câu 1..N, Tổng, Ghi chú — UTF-8 with BOM so Excel shows Vietnamese. */
    public byte[] exportCsv(UUID vivaExamId, User user) {
        ExamInfo exam = support.requireExamRead(vivaExamId, user);
        Instant now = clock.instant();
        List<GradingSupport.RosterRow> students = support.rosterOfExam(vivaExamId);

        // thread order numbers and final scores of every lượt thi of the exam
        Map<UUID, Integer> orderByAttemptQuestion = new HashMap<>();
        Integer maxOrder = support.jdbc().query("""
                        select sq.attempt_question_id, sq.order_no from attempt_questions sq
                        join exam_attempts s on s.attempt_id = sq.attempt_id where s.viva_exam_id = :exam""",
                new MapSqlParameterSource("exam", vivaExamId), rs -> {
                    int max = 0;
                    while (rs.next()) {
                        orderByAttemptQuestion.put(rs.getObject(1, UUID.class), rs.getInt(2));
                        max = Math.max(max, rs.getInt(2));
                    }
                    return max;
                });
        int questionCount = Math.max(exam.mainQuestionCount(), maxOrder == null ? 0 : maxOrder);

        Map<UUID, GradeEvaluation> evaluationByAttempt = evaluations.findByAttemptIdIn(students.stream()
                        .map(GradingSupport.RosterRow::attemptId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(GradeEvaluation::getAttemptId, Function.identity()));
        List<UUID> confirmedIds = evaluationByAttempt.values().stream()
                .filter(e -> e.getStatus() == EvaluationStatus.CONFIRMED).map(GradeEvaluation::getEvaluationId).toList();
        Map<UUID, List<QuestionGrade>> gradesByEvaluation = confirmedIds.isEmpty() ? Map.of()
                : grades.findByEvaluationIdIn(confirmedIds).stream()
                .collect(Collectors.groupingBy(QuestionGrade::getEvaluationId));

        StringBuilder csv = new StringBuilder();
        List<String> header = new ArrayList<>(List.of("STT", "MSSV", "Họ tên"));
        for (int i = 1; i <= questionCount; i++) {
            header.add("Câu " + i);
        }
        header.add("Tổng");
        if (exam.passScore() != null) {
            header.add("Kết quả");
        }
        header.add("Ghi chú");
        appendLine(csv, header);

        int stt = 0;
        for (GradingSupport.RosterRow s : students) {
            List<String> cells = new ArrayList<>();
            cells.add(String.valueOf(++stt));
            cells.add(nullToEmpty(s.studentCode()));
            cells.add(nullToEmpty(s.fullName()));
            String[] scores = new String[questionCount];
            String total = "";
            List<String> notes = new ArrayList<>();
            GradeEvaluation evaluation = s.attemptId() == null ? null : evaluationByAttempt.get(s.attemptId());
            if (!"COMPLETED".equals(s.attemptStatus())) {
                notes.add(stageLabel(stage(s.attemptStatus(), exam, now)));
            } else if (evaluation == null) {
                notes.add("Chưa chấm");
            } else if (evaluation.getStatus() == EvaluationStatus.DISPUTED) {
                notes.add("Đang phúc khảo");
            } else if (evaluation.getStatus() != EvaluationStatus.CONFIRMED) {
                notes.add("Chưa xác nhận điểm");
            } else {
                List<Integer> excluded = new ArrayList<>();
                for (QuestionGrade g : gradesByEvaluation.getOrDefault(evaluation.getEvaluationId(), List.of())) {
                    int order = orderByAttemptQuestion.getOrDefault(g.getAttemptQuestionId(), 0);
                    if (order < 1 || order > questionCount) {
                        continue;
                    }
                    if (g.isIncludeInTotal()) {
                        scores[order - 1] = format(g.getFinalScore());
                    } else {
                        excluded.add(order);
                    }
                }
                total = format(evaluation.getFinalTotalScore());
                if (!excluded.isEmpty()) {
                    notes.add("Không tính câu " + excluded.stream().sorted().map(String::valueOf)
                            .collect(Collectors.joining(", ")));
                }
                if (evaluation.getLecturerComment() != null) {
                    notes.add(evaluation.getLecturerComment());
                }
            }
            for (String score : scores) {
                cells.add(score == null ? "" : score);
            }
            cells.add(total);
            if (exam.passScore() != null) {
                cells.add(evaluation == null || evaluation.getStatus() != EvaluationStatus.CONFIRMED
                        || evaluation.getFinalTotalScore() == null ? ""
                        : evaluation.getFinalTotalScore().compareTo(exam.passScore()) >= 0 ? "Đạt" : "Không đạt");
            }
            cells.add(String.join("; ", notes));
            appendLine(csv, cells);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        out.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** Derived stage of a roster student (15 §2.2, D48) — same rule as the exam module's ExamStage. */
    static String stage(String attemptStatus, ExamInfo exam, Instant now) {
        if (attemptStatus != null) {
            return attemptStatus;
        }
        if ("CANCELLED".equals(exam.status())) {
            return "CANCELLED";
        }
        if ("CLOSED".equals(exam.status()) || now.isAfter(exam.checkinClosesAt())) {
            return "MISSED";
        }
        return "OPEN".equals(exam.status()) && !now.isBefore(exam.checkinOpensAt()) ? "AVAILABLE" : "UPCOMING";
    }

    private static String stageLabel(String stage) {
        return switch (stage) {
            case "UPCOMING" -> "Sắp diễn ra";
            case "AVAILABLE" -> "Chưa thi";
            case "IN_PROGRESS" -> "Đang thi";
            case "INTERRUPTED" -> "Bị gián đoạn";
            case "MISSED" -> "Vắng thi";
            case "CANCELLED" -> "Đã huỷ";
            default -> stage;
        };
    }

    private static Map<String, Integer> zeroCounts(List<String> keys) {
        Map<String, Integer> map = new LinkedHashMap<>();
        keys.forEach(k -> map.put(k, 0));
        return map;
    }

    private static String format(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void appendLine(StringBuilder csv, List<String> cells) {
        csv.append(cells.stream().map(ReportService::escape).collect(Collectors.joining(","))).append("\r\n");
    }

    private static String escape(String cell) {
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }
}
