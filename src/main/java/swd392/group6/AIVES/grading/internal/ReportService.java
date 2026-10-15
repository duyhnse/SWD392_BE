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
import swd392.group6.AIVES.questionbank.QuestionBankApi;
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
    private static final List<String> STATUSES =
            List.of("SCHEDULED", "IN_PROGRESS", "INTERRUPTED", "COMPLETED", "CANCELLED");

    private final GradingSupport support;
    private final GradeEvaluationRepository evaluations;
    private final QuestionGradeRepository grades;
    private final QuestionBankApi questionBankApi;
    private final Clock clock;

    public ReportDto report(UUID vivaExamId, User user) {
        ExamInfo exam = support.requireExamRead(vivaExamId, user);
        List<GradingSupport.SessionRow> sessions = support.sessionsOfExam(vivaExamId);
        Instant now = clock.instant();

        Map<String, Integer> byStatus = zeroCounts(STATUSES);
        Map<String, Integer> byStage = zeroCounts(STAGES);
        for (GradingSupport.SessionRow s : sessions) {
            byStatus.merge(s.status(), 1, Integer::sum);
            byStage.merge(stage(s, exam, now), 1, Integer::sum);
        }

        List<GradeEvaluation> evals = evaluations.findBySessionIdIn(
                sessions.stream().map(GradingSupport.SessionRow::sessionId).toList());
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
        return new ReportDto(vivaExamId, exam.title(), sessions.size(), byStatus, byStage, evals.size(),
                (int) evals.stream().filter(e -> e.getStatus() == EvaluationStatus.CONFIRMED).count(), stats,
                distribution, questions, hardest);
    }

    /**
     * Per main question of the buổi thi: times actually asked (thread has a turn, completed lượt thi), and over the
     * CONFIRMED evaluations the average thread final score and the share of finals ≥ 7.
     */
    private List<QuestionStats> questionStats(UUID vivaExamId) {
        record Row(UUID questionId, boolean asked, BigDecimal finalScore) {
        }
        List<Row> rows = support.jdbc().query("""
                        select sq.question_id,
                               exists (select 1 from exam_turns t where t.session_question_id = sq.session_question_id) as asked,
                               case when ge.status = 'CONFIRMED' then qg.final_score end as final_score
                        from exam_sessions s
                        join session_questions sq on sq.session_id = s.session_id
                        left join grade_evaluations ge on ge.session_id = s.session_id
                        left join question_grades qg on qg.evaluation_id = ge.evaluation_id
                                                     and qg.session_question_id = sq.session_question_id
                        where s.viva_exam_id = :exam and s.status = 'COMPLETED'""",
                new MapSqlParameterSource("exam", vivaExamId),
                (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getBoolean(2), rs.getBigDecimal(3)));
        Map<UUID, List<Row>> byQuestion = rows.stream()
                .collect(Collectors.groupingBy(Row::questionId, LinkedHashMap::new, Collectors.toList()));
        List<QuestionStats> result = new ArrayList<>();
        byQuestion.forEach((questionId, list) -> {
            List<BigDecimal> finals = list.stream().filter(Row::asked).map(Row::finalScore).filter(Objects::nonNull).toList();
            long good = finals.stream().filter(f -> f.compareTo(GOOD_ANSWER) >= 0).count();
            BigDecimal rate = finals.isEmpty() ? null
                    : BigDecimal.valueOf(good).divide(BigDecimal.valueOf(finals.size()), 4, RoundingMode.HALF_UP);
            String content = questionBankApi.getQuestion(questionId).map(QuestionBankApi.QuestionInfo::content).orElse(null);
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
        record StudentRow(UUID studentId, String fullName, String studentCode, UUID sessionId, String status,
                          String cancelReason) {
        }
        List<StudentRow> students = support.jdbc().query("""
                        select u.user_id, u.full_name, u.student_code, s.session_id, s.status, s.cancel_reason
                        from viva_exam_students vs
                        join users u on u.user_id = vs.student_id
                        left join exam_sessions s on s.viva_exam_id = vs.viva_exam_id and s.student_id = vs.student_id
                        where vs.viva_exam_id = :exam
                        order by vs.seq_no, u.student_code nulls last""",
                new MapSqlParameterSource("exam", vivaExamId),
                (rs, i) -> new StudentRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6)));

        // thread order numbers and final scores of every lượt thi of the exam
        Map<UUID, Integer> orderBySessionQuestion = new HashMap<>();
        Integer maxOrder = support.jdbc().query("""
                        select sq.session_question_id, sq.order_no from session_questions sq
                        join exam_sessions s on s.session_id = sq.session_id where s.viva_exam_id = :exam""",
                new MapSqlParameterSource("exam", vivaExamId), rs -> {
                    int max = 0;
                    while (rs.next()) {
                        orderBySessionQuestion.put(rs.getObject(1, UUID.class), rs.getInt(2));
                        max = Math.max(max, rs.getInt(2));
                    }
                    return max;
                });
        int questionCount = Math.max(exam.mainQuestionCount(), maxOrder == null ? 0 : maxOrder);

        Map<UUID, GradeEvaluation> evaluationBySession = evaluations.findBySessionIdIn(students.stream()
                        .map(StudentRow::sessionId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(GradeEvaluation::getSessionId, Function.identity()));
        List<UUID> confirmedIds = evaluationBySession.values().stream()
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
        header.add("Ghi chú");
        appendLine(csv, header);

        int stt = 0;
        for (StudentRow s : students) {
            List<String> cells = new ArrayList<>();
            cells.add(String.valueOf(++stt));
            cells.add(nullToEmpty(s.studentCode()));
            cells.add(nullToEmpty(s.fullName()));
            String[] scores = new String[questionCount];
            String total = "";
            List<String> notes = new ArrayList<>();
            GradeEvaluation evaluation = s.sessionId() == null ? null : evaluationBySession.get(s.sessionId());
            if (s.sessionId() == null) {
                notes.add("Chưa tạo lượt thi");
            } else if (!"COMPLETED".equals(s.status())) {
                notes.add(stageLabel(stage(new GradingSupport.SessionRow(s.sessionId(), s.studentId(), s.fullName(),
                        s.studentCode(), s.status(), s.cancelReason(), null), exam, now)));
            } else if (evaluation == null) {
                notes.add("Chưa chấm");
            } else if (evaluation.getStatus() == EvaluationStatus.DISPUTED) {
                notes.add("Đang phúc khảo");
            } else if (evaluation.getStatus() != EvaluationStatus.CONFIRMED) {
                notes.add("Chưa xác nhận điểm");
            } else {
                List<Integer> excluded = new ArrayList<>();
                for (QuestionGrade g : gradesByEvaluation.getOrDefault(evaluation.getEvaluationId(), List.of())) {
                    int order = orderBySessionQuestion.getOrDefault(g.getSessionQuestionId(), 0);
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
            cells.add(String.join("; ", notes));
            appendLine(csv, cells);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        out.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** Derived stage of a lượt thi (15 §2.2). */
    static String stage(GradingSupport.SessionRow s, ExamInfo exam, Instant now) {
        return switch (s.status()) {
            case "SCHEDULED" -> {
                boolean open = "OPEN".equals(exam.status()) || ("READY".equals(exam.status())
                        && !now.isBefore(exam.windowStart()) && !now.isAfter(exam.windowEnd()));
                yield open && !now.isBefore(exam.windowStart()) ? "AVAILABLE" : "UPCOMING";
            }
            case "CANCELLED" -> "NO_SHOW".equals(s.cancelReason()) ? "MISSED" : "CANCELLED";
            default -> s.status();
        };
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
