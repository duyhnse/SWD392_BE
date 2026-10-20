package swd392.group6.AIVES.grading.service;

import swd392.group6.AIVES.grading.domain.EvaluationStatus;
import swd392.group6.AIVES.grading.domain.GradeEvaluation;
import swd392.group6.AIVES.grading.domain.QuestionGrade;
import swd392.group6.AIVES.grading.repository.GradeEvaluationRepository;
import swd392.group6.AIVES.grading.repository.QuestionGradeRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.exam.ExamApi.ExamInfo;
import swd392.group6.AIVES.grading.dto.Bin;
import swd392.group6.AIVES.grading.dto.QuestionStats;
import swd392.group6.AIVES.grading.dto.ReportDto;
import swd392.group6.AIVES.grading.dto.ScoreStats;
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
public class ReportService {

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

    /** One student of the score sheet. Scores and totals only from a CONFIRMED evaluation (BR-G1). */
    record SheetRow(int stt, String studentCode, String fullName, String attendance, BigDecimal[] scores, BigDecimal total,
                    BigDecimal aiTotal, String result, String grading, String notes, UUID evaluationId) {
    }

    /** Header facts of the buổi thi for the sheet. */
    record SheetHeader(String courseCode, String courseName, Long examNo, String examTitle, Long templateNo,
                       String templateTitle, String examiner, Instant checkinOpensAt, Instant checkinClosesAt,
                       BigDecimal passScore, String language, int questionCount) {
    }

    record Sheet(SheetHeader header, List<SheetRow> rows, ReportDto report) {
    }

    /** Everything the CSV and the Excel score sheets show (FG6, 08 P3). */
    Sheet sheet(UUID vivaExamId, User user) {
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

        List<SheetRow> rows = new ArrayList<>();
        int stt = 0;
        for (GradingSupport.RosterRow s : students) {
            BigDecimal[] scores = new BigDecimal[questionCount];
            BigDecimal total = null;
            String result = "";
            List<String> notes = new ArrayList<>();
            GradeEvaluation evaluation = s.attemptId() == null ? null : evaluationByAttempt.get(s.attemptId());
            String attendance = stageLabel(stage(s.attemptStatus(), exam, now));
            String grading;
            if (!"COMPLETED".equals(s.attemptStatus())) {
                grading = "";
            } else if (evaluation == null) {
                grading = "Chưa chấm";
            } else if (evaluation.getStatus() == EvaluationStatus.DISPUTED) {
                grading = "Đang phúc khảo";
            } else if (evaluation.getStatus() != EvaluationStatus.CONFIRMED) {
                grading = evaluation.getStatus() == EvaluationStatus.PENDING_AI ? "AI đang chấm" : "Chờ giảng viên chốt";
            } else {
                grading = "Đã chốt";
                List<Integer> excluded = new ArrayList<>();
                for (QuestionGrade g : gradesByEvaluation.getOrDefault(evaluation.getEvaluationId(), List.of())) {
                    int order = orderByAttemptQuestion.getOrDefault(g.getAttemptQuestionId(), 0);
                    if (order < 1 || order > questionCount) {
                        continue;
                    }
                    if (g.isIncludeInTotal()) {
                        scores[order - 1] = g.getFinalScore();
                    } else {
                        excluded.add(order);
                    }
                }
                total = evaluation.getFinalTotalScore();
                if (exam.passScore() != null && total != null) {
                    result = total.compareTo(exam.passScore()) >= 0 ? "Đạt" : "Không đạt";
                }
                if (!excluded.isEmpty()) {
                    notes.add("Không tính câu " + excluded.stream().sorted().map(String::valueOf)
                            .collect(Collectors.joining(", ")));
                }
                if (evaluation.getLecturerComment() != null) {
                    notes.add(evaluation.getLecturerComment());
                }
            }
            if ("CANCELLED".equals(s.attemptStatus())) {
                notes.add("Lượt thi bị huỷ");
            }
            rows.add(new SheetRow(++stt, nullToEmpty(s.studentCode()), nullToEmpty(s.fullName()), attendance, scores, total,
                    evaluation == null ? null : evaluation.getAiTotalScore(), result, grading, String.join("; ", notes),
                    evaluation == null ? null : evaluation.getEvaluationId()));
        }
        Map<String, Object> h = support.jdbc().queryForMap("""
                        select c.code, c.name, e.display_no as exam_no, t.display_no as template_no, t.title as template_title,
                               u.full_name as examiner
                        from viva_exams e join courses c on c.course_id = e.course_id
                        join exam_templates t on t.exam_template_id = e.exam_template_id
                        left join users u on u.user_id = e.examiner_id
                        where e.viva_exam_id = :exam""", new MapSqlParameterSource("exam", vivaExamId));
        SheetHeader header = new SheetHeader((String) h.get("code"), (String) h.get("name"), number(h.get("exam_no")),
                exam.title(), number(h.get("template_no")), (String) h.get("template_title"), (String) h.get("examiner"),
                exam.checkinOpensAt(), exam.checkinClosesAt(), exam.passScore(), exam.language(), questionCount);
        return new Sheet(header, rows, report(vivaExamId, user));
    }

    private static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    /** Score sheet: STT, MSSV, Họ tên, Câu 1..N, Tổng, Kết quả, Ghi chú — UTF-8 with BOM so Excel shows Vietnamese. */
    public byte[] exportCsv(UUID vivaExamId, User user) {
        Sheet sheet = sheet(vivaExamId, user);
        int questionCount = sheet.header().questionCount();
        boolean pass = sheet.header().passScore() != null;
        StringBuilder csv = new StringBuilder();
        List<String> header = new ArrayList<>(List.of("STT", "MSSV", "Họ tên"));
        for (int i = 1; i <= questionCount; i++) {
            header.add("Câu " + i);
        }
        header.add("Tổng");
        if (pass) {
            header.add("Kết quả");
        }
        header.add("Ghi chú");
        appendLine(csv, header);
        for (SheetRow r : sheet.rows()) {
            List<String> cells = new ArrayList<>(List.of(String.valueOf(r.stt()), r.studentCode(), r.fullName()));
            for (BigDecimal score : r.scores()) {
                cells.add(format(score));
            }
            cells.add(format(r.total()));
            if (pass) {
                cells.add(r.result());
            }
            List<String> notes = new ArrayList<>();
            if (!"Đã thi".equals(r.attendance())) {
                notes.add(r.attendance());
            } else if (!"Đã chốt".equals(r.grading())) {
                notes.add("Chờ giảng viên chốt".equals(r.grading()) ? "Chưa xác nhận điểm" : r.grading());
            }
            if (!r.notes().isEmpty()) {
                notes.add(r.notes());
            }
            cells.add(String.join("; ", notes));
            appendLine(csv, cells);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        out.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** The same score sheet as an Excel workbook (sheet, criteria detail, question statistics). */
    public byte[] exportXlsx(UUID vivaExamId, User user) {
        return ScoreSheetXlsx.write(sheet(vivaExamId, user), criteriaDetail(vivaExamId), clock.instant());
    }

    /** Per student × question × criterion: AI and final scores of CONFIRMED evaluations (sheet 2 of the workbook). */
    List<ScoreSheetXlsx.CriterionLine> criteriaDetail(UUID vivaExamId) {
        return support.jdbc().query("""
                        select u.student_code, u.full_name, sq.order_no, sq.content, cs.criterion_id, qg.rubric_snapshot::text,
                               cs.weight_percent, cs.max_score, cs.ai_score, cs.final_score, qg.lecturer_comment, qg.include_in_total
                        from exam_attempts s join users u on u.user_id = s.student_id
                        join grade_evaluations ge on ge.attempt_id = s.attempt_id and ge.status = 'CONFIRMED'
                        join question_grades qg on qg.evaluation_id = ge.evaluation_id
                        join attempt_questions sq on sq.attempt_question_id = qg.attempt_question_id
                        join criterion_scores cs on cs.question_grade_id = qg.question_grade_id
                        where s.viva_exam_id = :exam
                        order by u.student_code, sq.order_no""", new MapSqlParameterSource("exam", vivaExamId),
                (rs, i) -> {
                    UUID criterionId = rs.getObject(5, UUID.class);
                    String name = GradingJson.snapshot(rs.getString(6)).criteria().stream()
                            .filter(c -> c.criterionId().equals(criterionId)).map(GradingJson.SnapshotCriterion::name)
                            .findFirst().orElse("");
                    return new ScoreSheetXlsx.CriterionLine(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4),
                            name, rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10),
                            rs.getString(11), rs.getBoolean(12));
                });
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
            case "COMPLETED" -> "Đã thi";
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
