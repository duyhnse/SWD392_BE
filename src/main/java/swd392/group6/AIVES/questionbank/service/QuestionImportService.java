package swd392.group6.AIVES.questionbank.service;

import swd392.group6.AIVES.questionbank.domain.Question;
import swd392.group6.AIVES.questionbank.domain.QuestionOrigin;
import swd392.group6.AIVES.questionbank.domain.QuestionStatus;
import swd392.group6.AIVES.questionbank.repository.QuestionRepository;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.CsvTemplates;
import swd392.group6.AIVES.common.DryRun;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;
import swd392.group6.AIVES.questionbank.dto.ImportReport;
import swd392.group6.AIVES.questionbank.dto.ImportRowError;
import swd392.group6.AIVES.user.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * C2: import questions from the CSV / XLSX template (04 §8). Good rows become DRAFT questions with
 * {@code origin=IMPORTED}; bad rows are reported with their spreadsheet row number and column (E2). The whole file
 * is rejected only when it cannot be read, lacks the required columns, or has more than {@value #MAX_ROWS} rows.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QuestionImportService {

    static final int MAX_ROWS = 500;
    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final List<String> COLUMNS = List.of("topic", "content", "reference_answer", "bloom_level", "language", "rubric_name");

    /** Vietnamese spellings accepted for Bloom (diacritics and case ignored). */
    private static final Map<String, BloomLevel> BLOOM_ALIASES = Map.of(
            "NHO", BloomLevel.REMEMBER, "HIEU", BloomLevel.UNDERSTAND,
            "VAN_DUNG", BloomLevel.APPLY, "PHAN_TICH", BloomLevel.ANALYZE);

    private final ContentAccess access;
    private final QuestionRepository questions;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    /** One data row with its spreadsheet row number (header = row 1). */
    record Line(int row, Map<String, String> cells) {
    }

    /** {@code dryRun}: validate and report only (the transaction is rolled back). */
    public ImportReport importFile(UUID courseId, MultipartFile file, User user, boolean dryRun) {
        access.writeCourse(courseId, user);
        if (file == null || file.isEmpty()) {
            throw ApiException.unprocessable("FILE_EMPTY", "Upload a non-empty file in the 'file' field");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "IMPORT_TOO_LARGE", "Import files are limited to 5 MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<Line> lines;
        try {
            lines = name.endsWith(".csv") ? readCsv(file.getBytes()) : null;
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ApiException.unprocessable("IMPORT_UNREADABLE", "The file could not be read as a UTF-8 CSV");
        }
        if (lines == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IMPORT_TYPE_UNSUPPORTED", "Only .csv files are accepted");
        }
        if (lines.size() > MAX_ROWS) {
            throw ApiException.unprocessable("IMPORT_TOO_MANY_ROWS", "A file has at most " + MAX_ROWS + " rows (found " + lines.size() + ")");
        }
        ImportReport report = importLines(courseId, lines, user);
        DryRun.rollbackIf(dryRun);
        return dryRun ? new ImportReport(report.total(), report.imported(), List.of(), report.errors()) : report;
    }

    ImportReport importLines(UUID courseId, List<Line> lines, User user) {
        Map<String, UUID> topics = new HashMap<>();
        jdbc.query("select topic_id, name from topics where course_id = ?", rs -> {
            topics.put(key(rs.getString(2)), rs.getObject(1, UUID.class));
        }, courseId);
        Map<String, UUID> rubrics = new HashMap<>();
        jdbc.query("select rubric_id, name from rubrics where course_id = ?", rs -> {
            rubrics.put(key(rs.getString(2)), rs.getObject(1, UUID.class));
        }, courseId);
        Set<String> existing = new HashSet<>();
        jdbc.query("select topic_id, content from questions where course_id = ? and status <> 'DISCARDED'", rs -> {
            existing.add(rs.getObject(1, UUID.class) + "|" + normalise(rs.getString(2)));
        }, courseId);
        Language courseLanguage = Language.valueOf(jdbc.queryForObject(
                "select default_language from courses where course_id = ?", String.class, courseId));

        List<ImportRowError> errors = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        Instant now = clock.instant();
        for (Line line : lines) {
            List<ImportRowError> rowErrors = new ArrayList<>();
            Map<String, String> c = line.cells();
            UUID topicId = topics.get(key(c.get("topic")));
            if (blank(c.get("topic"))) {
                rowErrors.add(error(line, "topic", "REQUIRED", "The topic is required"));
            } else if (topicId == null) {
                rowErrors.add(error(line, "topic", "TOPIC_NOT_FOUND", "No topic named \"" + c.get("topic") + "\" in this course"));
            }
            String content = trim(c.get("content"));
            if (content.length() < 10 || content.length() > 2000) {
                rowErrors.add(error(line, "content", "CONTENT_LENGTH", "The question must be 10–2000 characters"));
            }
            String reference = trim(c.get("reference_answer"));
            if (reference.length() > 4000) {
                rowErrors.add(error(line, "reference_answer", "REFERENCE_TOO_LONG", "The reference answer is at most 4000 characters"));
            }
            BloomLevel bloom = null;
            if (!blank(c.get("bloom_level"))) {
                bloom = bloom(c.get("bloom_level"));
                if (bloom == null) {
                    rowErrors.add(error(line, "bloom_level", "BLOOM_INVALID",
                            "Use REMEMBER, UNDERSTAND, APPLY, ANALYZE (or NHO, HIEU, VAN_DUNG, PHAN_TICH)"));
                }
            }
            Language language = courseLanguage;
            if (!blank(c.get("language"))) {
                try {
                    language = Language.valueOf(trim(c.get("language")).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    rowErrors.add(error(line, "language", "LANGUAGE_INVALID", "Use VI or EN"));
                }
            }
            UUID rubricId = null;
            if (!blank(c.get("rubric_name"))) {
                rubricId = rubrics.get(key(c.get("rubric_name")));
                if (rubricId == null) {
                    rowErrors.add(error(line, "rubric_name", "RUBRIC_NOT_FOUND", "No rubric named \"" + c.get("rubric_name") + "\" in this course"));
                }
            }
            String duplicateKey = topicId + "|" + normalise(content);
            if (topicId != null && content.length() >= 10 && existing.contains(duplicateKey)) {
                rowErrors.add(error(line, "content", "DUPLICATE", "The same question already exists in this topic (or earlier in the file)"));
            }
            if (!rowErrors.isEmpty()) {
                errors.addAll(rowErrors);
                continue;
            }
            existing.add(duplicateKey);
            Question q = new Question();
            q.setCourseId(courseId);
            q.setTopicId(topicId);
            q.setContent(content);
            q.setReferenceAnswer(reference.isEmpty() ? null : reference);
            q.setBloomLevel(bloom);
            q.setLanguage(language);
            q.setRubricId(rubricId);
            q.setStatus(QuestionStatus.DRAFT);
            q.setOrigin(QuestionOrigin.IMPORTED);
            q.setOwnerId(user.getUserId());
            q.setCreatedAt(now);
            q.setUpdatedAt(now);
            ids.add(questions.save(q).getId());
        }
        questions.flush();
        return new ImportReport(lines.size(), ids.size(), ids, errors);
    }

    /**
     * The CSV template (04 §8, D62): one header row, no data rows. Each header cell = column name + what to write +
     * what a blank cell means + a generic example (a basic maths question), never the data of a real course.
     */
    public byte[] template() {
        return CsvTemplates.csv(List.of(
                "topic (bắt buộc — tên một topic đã có trong môn; vd. Đạo hàm)",
                "content (bắt buộc — câu hỏi mở, 10–2000 ký tự; vd. Đạo hàm của hàm số y = x² là gì? Giải thích cách tính.)",
                "reference_answer (cần để công bố — các ý chính, cách nhau bằng xuống dòng; vd. - y' = 2x  - Dùng quy tắc (xⁿ)' = n·xⁿ⁻¹; trống = chưa có)",
                "bloom_level (cần để công bố — REMEMBER / UNDERSTAND / APPLY / ANALYZE hoặc NHO / HIEU / VAN_DUNG / PHAN_TICH; vd. HIEU; trống = chưa chọn)",
                "language (VI / EN — trống = ngôn ngữ của môn)",
                "rubric_name (cần để công bố — tên một rubric đã có trong môn; vd. Khái niệm & lập luận; trống = chưa gắn)"));
    }

    static List<Line> readCsv(byte[] data) throws IOException {
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true)
                .setIgnoreEmptyLines(true).get();
        try (CSVParser parser = CSVParser.parse(text, format)) {
            Map<String, String> headers = headers(parser.getHeaderNames());
            List<Line> lines = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> cells = new LinkedHashMap<>();
                headers.forEach((original, column) -> cells.put(column, record.isMapped(original) && record.isSet(original)
                        ? record.get(original) : ""));
                if (cells.values().stream().anyMatch(v -> !blank(v))) {
                    lines.add(new Line((int) record.getRecordNumber() + 1, cells));
                }
            }
            return lines;
        }
    }


    /** Original header → template column; the required columns must be there. */
    private static Map<String, String> headers(List<String> names) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String n : names) {
            String column = CsvTemplates.columnName(n);
            if (COLUMNS.contains(column) && !map.containsValue(column)) {
                map.put(n, column);
            }
        }
        if (!map.containsValue("topic") || !map.containsValue("content")) {
            throw ApiException.unprocessable("IMPORT_HEADER_INVALID",
                    "The first row must contain the columns " + String.join(", ", COLUMNS) + " (topic and content are required)");
        }
        return map;
    }

    static BloomLevel bloom(String raw) {
        String v = plain(raw).toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return BloomLevel.valueOf(v);
        } catch (IllegalArgumentException e) {
            return BLOOM_ALIASES.get(v);
        }
    }


    private static ImportRowError error(Line line, String column, String code, String message) {
        return new ImportRowError(line.row(), column, code, message);
    }

    private static String key(String s) {
        return s == null ? "" : s.strip().toLowerCase(Locale.ROOT);
    }

    /** Lower case, collapsed whitespace (04 §8 duplicate detection). */
    static String normalise(String s) {
        return s == null ? "" : s.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String plain(String s) {
        return Normalizer.normalize(trim(s).replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String trim(String s) {
        return s == null ? "" : s.strip();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
