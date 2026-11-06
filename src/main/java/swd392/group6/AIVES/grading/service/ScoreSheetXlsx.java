package swd392.group6.AIVES.grading.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import swd392.group6.AIVES.grading.dto.QuestionStats;
import swd392.group6.AIVES.grading.dto.ScoreStats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * FG6 score sheet as an Excel workbook (08 P3): (1) "Bảng điểm" — exam facts, one row per roster student (attendance,
 * final score per main question, total on 10, result against the pass mark, AI suggestion for reference, grading
 * status, notes), class summary and signature block, printable A4 landscape; (2) "Chi tiết tiêu chí" — AI and final
 * score per criterion; (3) "Thống kê câu hỏi" — times asked, average, share of good answers. Only CONFIRMED evaluations
 * give scores (BR-G1).
 */
public final class ScoreSheetXlsx {

    record CriterionLine(String studentCode, String fullName, int questionNo, String question, String criterion,
                         BigDecimal weightPercent, BigDecimal maxScore, BigDecimal aiScore, BigDecimal finalScore,
                         String lecturerComment, boolean includedInTotal) {
    }

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(VN);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd 'tháng' MM 'năm' yyyy").withZone(VN);

    private ScoreSheetXlsx() {
    }

    static byte[] write(ReportService.Sheet sheet, List<CriterionLine> criteria, Instant now) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            scoreSheet(wb.createSheet("Bảng điểm"), st, sheet, now);
            criteriaSheet(wb.createSheet("Chi tiết tiêu chí"), st, criteria);
            questionSheet(wb.createSheet("Thống kê câu hỏi"), st, sheet.report().questions(), sheet.report().hardestQuestions());
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------------------------------------- sheet 1

    private static void scoreSheet(Sheet ws, Styles st, ReportService.Sheet data, Instant now) {
        ReportService.SheetHeader h = data.header();
        int n = h.questionCount();
        int lastCol = 3 + n + 5; // STT MSSV Họ tên Dự thi | Câu 1..n | Tổng AI Kết quả Chấm Ghi chú
        int r = 0;
        title(ws, st, r++, lastCol, "BẢNG ĐIỂM THI VẤN ĐÁP");
        r++;
        r = fact(ws, st, r, lastCol, "Môn học", h.courseCode() + " – " + h.courseName());
        r = fact(ws, st, r, lastCol, "Buổi thi", code("B", h.examNo()) + h.examTitle());
        r = fact(ws, st, r, lastCol, "Đề thi", code("D", h.templateNo()) + nz(h.templateTitle())
                + " · " + n + " câu chính · ngôn ngữ " + ("EN".equals(h.language()) ? "tiếng Anh" : "tiếng Việt"));
        r = fact(ws, st, r, lastCol, "Giám khảo", nz(h.examiner()));
        r = fact(ws, st, r, lastCol, "Thời gian check-in", TIME.format(h.checkinOpensAt()) + " – " + TIME.format(h.checkinClosesAt()));
        r = fact(ws, st, r, lastCol, "Thang điểm", "10" + (h.weighted() ? " · mỗi câu có điểm tối đa theo đề thi, ô điểm = điểm đạt được" : "") + (h.passScore() != null ? " · điểm đạt " + num(h.passScore()) : ""));
        r = fact(ws, st, r, lastCol, "Ngày xuất", TIME.format(now));
        r++;

        int headerRow = r;
        Row head = ws.createRow(r++);
        head.setHeightInPoints(30);
        int c = 0;
        for (String t : List.of("STT", "MSSV", "Họ và tên", "Dự thi")) {
            cell(head, c++, t, st.head);
        }
        for (int i = 1; i <= n; i++) {
            BigDecimal points = h.questionPoints().get(i);
            cell(head, c++, "Câu " + i + (points == null ? "" : " (" + num(points) + " đ)"), st.head);
        }
        for (String t : List.of("Tổng điểm", "AI gợi ý", "Kết quả", "Trạng thái chấm", "Ghi chú")) {
            cell(head, c++, t, st.head);
        }
        for (ReportService.SheetRow row : data.rows()) {
            Row x = ws.createRow(r++);
            c = 0;
            cell(x, c++, row.stt(), st.center);
            cell(x, c++, row.studentCode(), st.center);
            cell(x, c++, row.fullName(), st.text);
            cell(x, c++, row.attendance(), st.center);
            for (BigDecimal score : row.scores()) {
                cell(x, c++, score, st.score);
            }
            cell(x, c++, row.total(), st.total);
            cell(x, c++, row.aiTotal(), st.ai);
            cell(x, c++, row.result(), "Không đạt".equals(row.result()) ? st.fail : st.center);
            cell(x, c++, row.grading(), st.center);
            cell(x, c, row.notes(), st.wrap);
        }
        ws.setAutoFilter(new CellRangeAddress(headerRow, r - 1, 0, lastCol));
        ws.createFreezePane(3, headerRow + 1);

        // class summary
        r++;
        ScoreStats s = data.report().finalTotals();
        long attended = data.rows().stream().filter(x -> "Đã thi".equals(x.attendance())).count();
        long missed = data.rows().stream().filter(x -> "Vắng thi".equals(x.attendance())).count();
        long passed = data.rows().stream().filter(x -> "Đạt".equals(x.result())).count();
        long failed = data.rows().stream().filter(x -> "Không đạt".equals(x.result())).count();
        Row sumHead = ws.createRow(r++);
        cell(sumHead, 1, "THỐNG KÊ", st.head);
        ws.addMergedRegion(new CellRangeAddress(sumHead.getRowNum(), sumHead.getRowNum(), 1, 3));
        r = stat(ws, st, r, "Sĩ số", data.rows().size());
        r = stat(ws, st, r, "Dự thi", attended);
        r = stat(ws, st, r, "Vắng thi", missed);
        r = stat(ws, st, r, "Đã chốt điểm", s.count());
        if (h.passScore() != null) {
            r = stat(ws, st, r, "Đạt", passed);
            r = stat(ws, st, r, "Không đạt", failed);
        }
        r = stat(ws, st, r, "Điểm trung bình", s.mean());
        r = stat(ws, st, r, "Trung vị", s.median());
        r = stat(ws, st, r, "Cao nhất", s.max());
        r = stat(ws, st, r, "Thấp nhất", s.min());
        int[][] bands = {{0, 4}, {4, 5}, {5, 7}, {7, 9}, {9, 11}};
        String[] labels = {"Dưới 4", "4 – dưới 5", "5 – dưới 7", "7 – dưới 9", "9 – 10"};
        for (int i = 0; i < bands.length; i++) {
            int from = bands[i][0], to = bands[i][1];
            long count = data.rows().stream().filter(x -> x.total() != null)
                    .filter(x -> x.total().doubleValue() >= from && x.total().doubleValue() < to).count();
            r = stat(ws, st, r, "Phổ điểm " + labels[i], count);
        }

        // signatures
        r += 2;
        Row date = ws.createRow(r++);
        int sigCol = Math.max(4, lastCol - 3);
        cell(date, sigCol, "Ngày " + DAY.format(now), st.italicCenter);
        ws.addMergedRegion(new CellRangeAddress(date.getRowNum(), date.getRowNum(), sigCol, lastCol));
        Row sig = ws.createRow(r++);
        cell(sig, 1, "Trưởng bộ môn", st.boldCenter);
        ws.addMergedRegion(new CellRangeAddress(sig.getRowNum(), sig.getRowNum(), 1, 2));
        cell(sig, sigCol, "Giám khảo", st.boldCenter);
        ws.addMergedRegion(new CellRangeAddress(sig.getRowNum(), sig.getRowNum(), sigCol, lastCol));
        Row hint = ws.createRow(r++);
        cell(hint, 1, "(Ký, ghi rõ họ tên)", st.italicCenter);
        ws.addMergedRegion(new CellRangeAddress(hint.getRowNum(), hint.getRowNum(), 1, 2));
        cell(hint, sigCol, "(Ký, ghi rõ họ tên)", st.italicCenter);
        ws.addMergedRegion(new CellRangeAddress(hint.getRowNum(), hint.getRowNum(), sigCol, lastCol));
        r += 3;
        Row name = ws.createRow(r);
        cell(name, sigCol, nz(h.examiner()), st.boldCenter);
        ws.addMergedRegion(new CellRangeAddress(name.getRowNum(), name.getRowNum(), sigCol, lastCol));

        int[] widths = new int[lastCol + 1];
        widths[0] = 6; widths[1] = 13; widths[2] = 28; widths[3] = 12;
        for (int i = 0; i < n; i++) {
            widths[4 + i] = 8;
        }
        widths[4 + n] = 11; widths[5 + n] = 10; widths[6 + n] = 11; widths[7 + n] = 18; widths[8 + n] = 40;
        for (int i = 0; i <= lastCol; i++) {
            ws.setColumnWidth(i, widths[i] * 256);
        }
        ws.getPrintSetup().setLandscape(true);
        ws.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        ws.setFitToPage(true);
        ws.getPrintSetup().setFitWidth((short) 1);
        ws.getPrintSetup().setFitHeight((short) 0);
        ws.setRepeatingRows(new CellRangeAddress(headerRow, headerRow, -1, -1));
    }

    // ---------------------------------------------------------------------------------------------- sheets 2, 3

    private static void criteriaSheet(Sheet ws, Styles st, List<CriterionLine> lines) {
        Row head = ws.createRow(0);
        String[] titles = {"MSSV", "Họ và tên", "Câu", "Nội dung câu hỏi", "Tiêu chí", "Trọng số (%)", "Điểm tối đa",
                "AI gợi ý", "Điểm chốt", "Tính vào tổng", "Nhận xét của giảng viên"};
        for (int i = 0; i < titles.length; i++) {
            cell(head, i, titles[i], st.head);
        }
        int r = 1;
        for (CriterionLine l : lines) {
            Row x = ws.createRow(r++);
            cell(x, 0, l.studentCode(), st.center);
            cell(x, 1, l.fullName(), st.text);
            cell(x, 2, l.questionNo(), st.center);
            cell(x, 3, l.question(), st.wrap);
            cell(x, 4, l.criterion(), st.text);
            cell(x, 5, l.weightPercent(), st.score);
            cell(x, 6, l.maxScore(), st.score);
            cell(x, 7, l.aiScore(), st.ai);
            cell(x, 8, l.finalScore(), st.total);
            cell(x, 9, l.includedInTotal() ? "Có" : "Không", st.center);
            cell(x, 10, l.lecturerComment(), st.wrap);
        }
        int[] widths = {13, 26, 6, 50, 26, 12, 11, 10, 10, 12, 40};
        for (int i = 0; i < widths.length; i++) {
            ws.setColumnWidth(i, widths[i] * 256);
        }
        if (r > 1) {
            ws.setAutoFilter(new CellRangeAddress(0, r - 1, 0, titles.length - 1));
        }
        ws.createFreezePane(2, 1);
    }

    private static void questionSheet(Sheet ws, Styles st, List<QuestionStats> questions, List<QuestionStats> hardest) {
        Row head = ws.createRow(0);
        String[] titles = {"Câu hỏi", "Số lần hỏi", "Số bài đã chốt", "Điểm trung bình", "Tỉ lệ trả lời tốt (≥ 7)", "Khó nhất"};
        for (int i = 0; i < titles.length; i++) {
            cell(head, i, titles[i], st.head);
        }
        int r = 1;
        for (QuestionStats q : questions) {
            Row x = ws.createRow(r++);
            cell(x, 0, q.content(), st.wrap);
            cell(x, 1, q.timesAsked(), st.center);
            cell(x, 2, q.gradedCount(), st.center);
            cell(x, 3, q.averageFinalScore(), st.total);
            cell(x, 4, q.goodAnswerRate() == null ? null : q.goodAnswerRate().multiply(BigDecimal.valueOf(100)), st.percent);
            cell(x, 5, hardest.stream().anyMatch(hq -> Objects.equals(hq.questionId(), q.questionId())) ? "✓" : "", st.center);
        }
        int[] widths = {70, 11, 14, 15, 20, 10};
        for (int i = 0; i < widths.length; i++) {
            ws.setColumnWidth(i, widths[i] * 256);
        }
        ws.createFreezePane(0, 1);
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private static void title(Sheet ws, Styles st, int r, int lastCol, String text) {
        Row row = ws.createRow(r);
        row.setHeightInPoints(26);
        cell(row, 0, text, st.title);
        ws.addMergedRegion(new CellRangeAddress(r, r, 0, lastCol));
    }

    private static int fact(Sheet ws, Styles st, int r, int lastCol, String label, String value) {
        Row row = ws.createRow(r);
        cell(row, 0, label, st.label);
        ws.addMergedRegion(new CellRangeAddress(r, r, 0, 2));
        cell(row, 3, value, st.plain);
        ws.addMergedRegion(new CellRangeAddress(r, r, 3, lastCol));
        return r + 1;
    }

    private static int stat(Sheet ws, Styles st, int r, String label, Object value) {
        Row row = ws.createRow(r);
        cell(row, 1, label, st.text);
        ws.addMergedRegion(new CellRangeAddress(r, r, 1, 2));
        if (value instanceof BigDecimal d) {
            cell(row, 3, d, st.total);
        } else {
            cell(row, 3, value == null ? null : ((Number) value).intValue(), st.center);
        }
        return r + 1;
    }

    private static void cell(Row row, int col, Object value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellStyle(style);
        if (value instanceof BigDecimal d) {
            c.setCellValue(d.doubleValue());
        } else if (value instanceof Number num) {
            c.setCellValue(num.doubleValue());
        } else if (value != null) {
            c.setCellValue(value.toString());
        }
    }

    private static String code(String kind, Long no) {
        return no == null ? "" : kind + "-" + no + " · ";
    }

    private static String num(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /** Cell styles of the workbook (Times New Roman, bordered table, scores with 2 decimals). */
    private static final class Styles {
        final CellStyle title, label, plain, head, text, center, wrap, score, total, ai, fail, percent, boldCenter, italicCenter;

        Styles(XSSFWorkbook wb) {
            short twoDecimals = wb.createDataFormat().getFormat("0.00");
            title = style(wb, font(wb, 16, true, false, IndexedColors.BLACK), false, HorizontalAlignment.CENTER);
            label = style(wb, font(wb, 11, true, false, IndexedColors.BLACK), false, HorizontalAlignment.LEFT);
            plain = style(wb, font(wb, 11, false, false, IndexedColors.BLACK), false, HorizontalAlignment.LEFT);
            head = style(wb, font(wb, 11, true, false, IndexedColors.WHITE), true, HorizontalAlignment.CENTER);
            head.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            head.setWrapText(true);
            text = style(wb, font(wb, 11, false, false, IndexedColors.BLACK), true, HorizontalAlignment.LEFT);
            center = style(wb, font(wb, 11, false, false, IndexedColors.BLACK), true, HorizontalAlignment.CENTER);
            wrap = style(wb, font(wb, 11, false, false, IndexedColors.BLACK), true, HorizontalAlignment.LEFT);
            wrap.setWrapText(true);
            score = style(wb, font(wb, 11, false, false, IndexedColors.BLACK), true, HorizontalAlignment.CENTER);
            score.setDataFormat(twoDecimals);
            total = style(wb, font(wb, 11, true, false, IndexedColors.BLACK), true, HorizontalAlignment.CENTER);
            total.setDataFormat(twoDecimals);
            ai = style(wb, font(wb, 11, false, true, IndexedColors.VIOLET), true, HorizontalAlignment.CENTER);
            ai.setDataFormat(twoDecimals);
            fail = style(wb, font(wb, 11, true, false, IndexedColors.RED), true, HorizontalAlignment.CENTER);
            percent = style(wb, font(wb, 11, false, false, IndexedColors.BLACK), true, HorizontalAlignment.CENTER);
            percent.setDataFormat(wb.createDataFormat().getFormat("0\"%\""));
            boldCenter = style(wb, font(wb, 11, true, false, IndexedColors.BLACK), false, HorizontalAlignment.CENTER);
            italicCenter = style(wb, font(wb, 11, false, true, IndexedColors.BLACK), false, HorizontalAlignment.CENTER);
        }

        private static Font font(XSSFWorkbook wb, int size, boolean bold, boolean italic, IndexedColors color) {
            Font f = wb.createFont();
            f.setFontName("Times New Roman");
            f.setFontHeightInPoints((short) size);
            f.setBold(bold);
            f.setItalic(italic);
            f.setColor(color.getIndex());
            return f;
        }

        private static CellStyle style(XSSFWorkbook wb, Font font, boolean border, HorizontalAlignment align) {
            CellStyle s = wb.createCellStyle();
            s.setFont(font);
            s.setAlignment(align);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            if (border) {
                s.setBorderTop(BorderStyle.THIN);
                s.setBorderBottom(BorderStyle.THIN);
                s.setBorderLeft(BorderStyle.THIN);
                s.setBorderRight(BorderStyle.THIN);
            }
            return s;
        }
    }
}
