package swd392.group6.AIVES.common;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bulk import templates (15 §5.7, D62). Row 1 of every template is both the header and the help: each cell starts
 * with the column name and continues with what to write and what a blank cell means, e.g.
 * {@code role (STUDENT / LECTURER / ADMIN — trống = STUDENT)}. Importers read only the leading name, so a file whose
 * header is just the names works too. There are no example rows: an untouched template imports nothing.
 */
public final class CsvTemplates {

    private static final Pattern NAME = Pattern.compile("^[\\s\\uFEFF\"]*([A-Za-z_]+)");

    private CsvTemplates() {
    }

    /** "username (bắt buộc …)" → "username"; "Full Name" → "full"; null / no name → "". */
    public static String columnName(String headerCell) {
        if (headerCell == null) {
            return "";
        }
        Matcher m = NAME.matcher(headerCell);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : "";
    }

    /** UTF-8 CSV with a BOM (so Excel shows Vietnamese correctly) holding only the header row. */
    public static byte[] csv(List<String> headerCells) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            printer.printRecord(headerCells);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** TRUE/FALSE, 1/0, YES/NO, CÓ/KHÔNG (any case); blank → null; anything else → IllegalArgumentException. */
    public static Boolean bool(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "TRUE", "1", "YES", "Y", "CÓ", "CO", "X" -> true;
            case "FALSE", "0", "NO", "N", "KHÔNG", "KHONG" -> false;
            default -> throw new IllegalArgumentException(value);
        };
    }
}
