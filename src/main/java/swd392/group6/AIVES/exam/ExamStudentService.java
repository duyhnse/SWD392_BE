package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamDtos.AddStudentsReport;
import swd392.group6.AIVES.exam.ExamDtos.ImportReport;
import swd392.group6.AIVES.exam.ExamDtos.ImportRowError;
import swd392.group6.AIVES.exam.ExamDtos.StudentRef;
import swd392.group6.AIVES.exam.ExamDtos.StudentView;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;
import swd392.group6.AIVES.user.UserApi;
import swd392.group6.AIVES.user.UserApi.NewAccount;
import swd392.group6.AIVES.user.UserApi.ProvisionResult;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Student list of a buổi thi: paste codes / usernames, class-list CSV, removal (15 §5.3, D28). */
@Service
@RequiredArgsConstructor
@Transactional
class ExamStudentService {

    static final int MAX_IMPORT_ROWS = 1000;
    private static final Pattern SEPARATORS = Pattern.compile("[,;\\s]+");

    private final ExamAccess access;
    private final ExamStatusRefresher refresher;
    private final UserApi userApi;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    List<StudentView> list(UUID examId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.read(examId, user);
        Instant now = clock.instant();
        return jdbc.query("""
                        select v.student_id, u.username, u.full_name, u.student_code, v.seq_no, v.added_at,
                               a.attempt_id, a.status
                        from viva_exam_students v join users u on u.user_id = v.student_id
                        left join exam_attempts a on a.viva_exam_id = v.viva_exam_id and a.student_id = v.student_id
                        where v.viva_exam_id = :e order by v.seq_no""", ExamQueries.params(examId),
                (rs, i) -> new StudentView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), ExamQueries.instant(rs.getTimestamp(6)), rs.getObject(7, UUID.class), rs.getString(8),
                        ExamStage.of(rs.getString(8), exam.getStatus(), exam.getCheckinOpensAt(),
                                exam.getCheckinClosesAt(), now)));
    }

    AddStudentsReport add(UUID examId, Object studentCodes, Object usernames, User user) {
        refresher.refreshDue();
        requireRosterEditable(access.write(examId, user));
        return addResolved(examId, tokens(studentCodes, true), tokens(usernames, false), null, user.getUserId());
    }

    /**
     * Matches codes (case-insensitive) and usernames to existing users and appends the active students in the
     * given order. Nothing is created (D28).
     *
     * @param allowed when not null, only these students may be added; the others are reported as notInOriginal
     */
    AddStudentsReport addResolved(UUID examId, List<String> codes, List<String> usernames, Set<UUID> allowed, UUID actor) {
        Map<String, Account> byCode = lookup("upper(student_code)", codes, a -> a.studentCode().toUpperCase(Locale.ROOT));
        Map<String, Account> byUsername = lookup("lower(username)", usernames, a -> a.username().toLowerCase(Locale.ROOT));

        List<StudentRef> added = new ArrayList<>();
        List<String> alreadyInExam = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> notStudent = new ArrayList<>();
        List<String> inactive = new ArrayList<>();
        List<String> notInOriginal = new ArrayList<>();
        Set<UUID> inExam = new HashSet<>(jdbc.queryForList(
                "select student_id from viva_exam_students where viva_exam_id = :e", ExamQueries.params(examId), UUID.class));
        int seq = nextSeq(examId);

        List<Map.Entry<String, Account>> resolved = new ArrayList<>();
        codes.forEach(c -> resolved.add(new java.util.AbstractMap.SimpleEntry<>(c, byCode.get(c))));
        usernames.forEach(u -> resolved.add(new java.util.AbstractMap.SimpleEntry<>(u, byUsername.get(u))));
        for (Map.Entry<String, Account> entry : resolved) {
            String token = entry.getKey();
            Account a = entry.getValue();
            if (a == null) {
                unknown.add(token);
            } else if (a.roleId() != Role.STUDENT.getId()) {
                notStudent.add(token);
            } else if (!a.active()) {
                inactive.add(token);
            } else if (allowed != null && !allowed.contains(a.userId())) {
                notInOriginal.add(token);
            } else if (inExam.contains(a.userId())) {
                alreadyInExam.add(token);
            } else {
                insert(examId, a.userId(), seq, actor);
                inExam.add(a.userId());
                added.add(new StudentRef(a.userId(), a.username(), a.studentCode(), a.fullName(), seq));
                seq++;
            }
        }
        return new AddStudentsReport(added, alreadyInExam, unknown, notStudent, inactive, notInOriginal);
    }

    /** Class list CSV (username, full_name, email, student_code): missing students are created by the user module. */
    ImportReport importCsv(UUID examId, InputStream input, User user) {
        refresher.refreshDue();
        requireRosterEditable(access.write(examId, user));

        List<NewAccount> accounts = new ArrayList<>();
        List<Integer> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(skipBom(input), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).setTrim(true).get().parse(reader)) {
            Map<String, Integer> header = new HashMap<>();
            parser.getHeaderMap().forEach((name, index) -> header.put(name.trim().toLowerCase(Locale.ROOT), index));
            if (!header.keySet().containsAll(Set.of("username", "full_name", "email"))) {
                throw invalidFile("Header must contain: username, full_name, email (optional: student_code)");
            }
            for (CSVRecord record : parser) {
                if (accounts.size() >= MAX_IMPORT_ROWS) {
                    throw invalidFile("A file may contain at most " + MAX_IMPORT_ROWS + " rows");
                }
                accounts.add(NewAccount.student(value(record, header, "username"), value(record, header, "full_name"),
                        value(record, header, "email"), value(record, header, "student_code")));
                rows.add((int) record.getRecordNumber() + 1);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw invalidFile("The file is not a readable UTF-8 CSV");
        }

        List<ProvisionResult> results = userApi.ensureStudents(accounts);
        List<ImportRowError> errors = new ArrayList<>();
        List<String> usernames = new ArrayList<>();
        int created = 0;
        for (int i = 0; i < results.size(); i++) {
            ProvisionResult result = results.get(i);
            if (!result.ok()) {
                errors.add(new ImportRowError(rows.get(i), result.errorField(), result.errorCode(), result.message()));
                continue;
            }
            if (result.created()) {
                created++;
            }
            String username = accounts.get(i).username().trim().toLowerCase(Locale.ROOT);
            if (!usernames.contains(username)) {
                usernames.add(username);
            }
        }
        AddStudentsReport report = addResolved(examId, List.of(), usernames, null, user.getUserId());
        List<String> notStudent = new ArrayList<>(report.notStudent());
        notStudent.addAll(report.inactive());
        return new ImportReport(accounts.size(), created, report.added(), report.alreadyInExam(), notStudent, errors);
    }

    /**
     * Only students who have not checked in can leave the roster; the others keep a contiguous order. A published or
     * open buổi thi keeps at least one student, as publishing required (unpublish or cancel it instead).
     */
    void remove(UUID examId, UUID studentId, User user) {
        refresher.refreshDue();
        VivaExam exam = access.write(examId, user);
        requireRosterEditable(exam);
        MapSqlParameterSource p = ExamQueries.params(examId).addValue("s", studentId);
        List<Integer> seq = jdbc.queryForList(
                "select seq_no from viva_exam_students where viva_exam_id = :e and student_id = :s", p, Integer.class);
        if (seq.isEmpty()) {
            throw ApiException.notFound("STUDENT_NOT_IN_EXAM", "The student is not in this exam");
        }
        Boolean checkedIn = jdbc.queryForObject(
                "select exists(select 1 from exam_attempts where viva_exam_id = :e and student_id = :s)", p, Boolean.class);
        if (Boolean.TRUE.equals(checkedIn)) {
            throw ApiException.conflict("ATTEMPT_EXISTS", "The student already checked in");
        }
        Integer roster = jdbc.queryForObject("select count(*) from viva_exam_students where viva_exam_id = :e", p, Integer.class);
        if (exam.getStatus() != ExamStatus.DRAFT && roster != null && roster <= 1) {
            throw ApiException.conflict("LAST_STUDENT",
                    "A published or open exam needs at least one student; unpublish or cancel it instead");
        }
        jdbc.update("delete from viva_exam_students where viva_exam_id = :e and student_id = :s", p);
        jdbc.update("update viva_exam_students set seq_no = seq_no - 1 where viva_exam_id = :e and seq_no > :seq",
                p.addValue("seq", seq.getFirst()));
    }

    /** The roster can change until check-in closes: students draw their questions only at check-in (D48). */
    private static void requireRosterEditable(VivaExam exam) {
        if (exam.getStatus() == ExamStatus.CLOSED || exam.getStatus() == ExamStatus.CANCELLED) {
            throw ApiException.conflict("EXAM_NOT_EDITABLE", "The student list of a closed or cancelled exam is final");
        }
    }

    /** Splits pasted text on commas, semicolons, whitespace and new lines; de-duplicates case-insensitively. */
    static List<String> tokens(Object raw, boolean upperCase) {
        Set<String> out = new LinkedHashSet<>();
        List<Object> parts = new ArrayList<>();
        if (raw instanceof Collection<?> c) {
            parts.addAll(c);
        } else if (raw != null) {
            parts.add(raw);
        }
        for (Object part : parts) {
            if (part == null) {
                continue;
            }
            for (String token : SEPARATORS.split(part.toString())) {
                if (!token.isBlank()) {
                    out.add(upperCase ? token.toUpperCase(Locale.ROOT) : token.toLowerCase(Locale.ROOT));
                }
            }
        }
        return List.copyOf(out);
    }

    private record Account(UUID userId, String username, String fullName, String studentCode, short roleId,
                           boolean active) {
    }

    private Map<String, Account> lookup(String column, List<String> keys, Function<Account, String> keyOf) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, Account> map = new HashMap<>();
        jdbc.query("select user_id, username, full_name, student_code, role_id, is_active from users where "
                        + column + " in (:keys)", new MapSqlParameterSource("keys", keys),
                (ResultSet rs) -> {
                    Account a = account(rs);
                    map.put(keyOf.apply(a), a);
                });
        return map;
    }

    private static Account account(ResultSet rs) throws SQLException {
        return new Account(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getShort(5), rs.getBoolean(6));
    }

    private int nextSeq(UUID examId) {
        Integer max = jdbc.queryForObject("select coalesce(max(seq_no), 0) from viva_exam_students where viva_exam_id = :e",
                ExamQueries.params(examId), Integer.class);
        return (max == null ? 0 : max) + 1;
    }

    private void insert(UUID examId, UUID studentId, int seq, UUID actor) {
        jdbc.update("""
                insert into viva_exam_students (viva_exam_id, student_id, seq_no, added_at, added_by)
                values (:e, :s, :seq, :at, :by)""", ExamQueries.params(examId).addValue("s", studentId)
                .addValue("seq", seq).addValue("at", java.sql.Timestamp.from(clock.instant())).addValue("by", actor));
    }

    private static String value(CSVRecord record, Map<String, Integer> header, String column) {
        Integer index = header.get(column);
        return index == null || index >= record.size() ? null : record.get(index);
    }

    private static InputStream skipBom(InputStream input) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(input);
        buffered.mark(3);
        byte[] bom = buffered.readNBytes(3);
        if (!(bom.length == 3 && (bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF)) {
            buffered.reset();
        }
        return buffered;
    }

    private static ApiException invalidFile(String message) {
        return ApiException.unprocessable("IMPORT_FILE_INVALID", message);
    }
}
