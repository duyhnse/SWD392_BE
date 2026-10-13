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
import swd392.group6.AIVES.common.CsvTemplates;
import swd392.group6.AIVES.common.DryRun;
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

    /**
     * Class list CSV (columns {@code student_code, username, full_name, email}, any order). A row with only a student
     * code (or a username) adds an existing student; a row that also has username, full name and email creates the
     * account when it is missing (user module). Every problem is reported on its row.
     */
    ImportReport importCsv(UUID examId, InputStream input, User user, boolean dryRun) {
        refresher.refreshDue();
        requireRosterEditable(access.write(examId, user));

        record Row(int row, String code, String username, String fullName, String email, String password) {
            boolean creates() {
                return notBlank(username) && notBlank(fullName) && notBlank(email);
            }
        }
        List<Row> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(skipBom(input), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).setTrim(true).get().parse(reader)) {
            Map<String, Integer> header = new HashMap<>();
            parser.getHeaderMap().forEach((name, index) -> header.putIfAbsent(CsvTemplates.columnName(name), index));
            if (!header.containsKey("student_code") && !header.containsKey("username")) {
                throw invalidFile("Header must contain student_code (or username); add username, full_name, email to create missing accounts");
            }
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_IMPORT_ROWS) {
                    throw invalidFile("A file may contain at most " + MAX_IMPORT_ROWS + " rows");
                }
                Row r = new Row((int) record.getRecordNumber() + 1, value(record, header, "student_code"),
                        value(record, header, "username"), value(record, header, "full_name"), value(record, header, "email"),
                        value(record, header, "password"));
                if (notBlank(r.code()) || notBlank(r.username()) || notBlank(r.fullName()) || notBlank(r.email())) {
                    rows.add(r);
                }
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw invalidFile("The file is not a readable UTF-8 CSV");
        }

        List<ImportRowError> errors = new ArrayList<>();
        List<StudentRef> added = new ArrayList<>();
        List<String> alreadyInExam = new ArrayList<>();
        List<String> notStudent = new ArrayList<>();
        int created = 0;

        List<Row> creating = rows.stream().filter(Row::creates).toList();
        List<ProvisionResult> results = userApi.ensureStudents(creating.stream()
                .map(r -> NewAccount.student(r.username(), r.fullName(), r.email(), r.code(), r.password())).toList());
        for (int i = 0; i < results.size(); i++) {
            ProvisionResult result = results.get(i);
            Row r = creating.get(i);
            if (!result.ok()) {
                errors.add(new ImportRowError(r.row(), result.errorField(), result.errorCode(), result.message()));
                continue;
            }
            if (result.created()) {
                created++;
            }
            collect(addResolved(examId, List.of(), List.of(r.username().trim().toLowerCase(Locale.ROOT)), null, user.getUserId()),
                    r.row(), "username", added, alreadyInExam, notStudent, errors);
        }
        for (Row r : rows) {
            if (r.creates()) {
                continue;
            }
            boolean byCode = notBlank(r.code());
            if (!byCode && !notBlank(r.username())) {
                errors.add(new ImportRowError(r.row(), "student_code", "STUDENT_CODE_REQUIRED",
                        "Give the student code (or the username) of the student"));
                continue;
            }
            AddStudentsReport report = byCode
                    ? addResolved(examId, List.of(r.code().trim().toUpperCase(Locale.ROOT)), List.of(), null, user.getUserId())
                    : addResolved(examId, List.of(), List.of(r.username().trim().toLowerCase(Locale.ROOT)), null, user.getUserId());
            collect(report, r.row(), byCode ? "student_code" : "username", added, alreadyInExam, notStudent, errors);
        }
        errors.sort((x, y) -> Integer.compare(x.row(), y.row()));
        DryRun.rollbackIf(dryRun);
        return new ImportReport(rows.size(), created, added, alreadyInExam, notStudent, errors);
    }

    /** One row's outcome: added / already there, or a row error (unknown, not a student, deactivated). */
    private static void collect(AddStudentsReport report, int row, String column, List<StudentRef> added,
                                List<String> alreadyInExam, List<String> notStudent, List<ImportRowError> errors) {
        added.addAll(report.added());
        alreadyInExam.addAll(report.alreadyInExam());
        report.unknown().forEach(t -> errors.add(new ImportRowError(row, column, "STUDENT_NOT_FOUND",
                "No account " + t + "; add username, full_name and email to create it")));
        report.notStudent().forEach(t -> {
            notStudent.add(t);
            errors.add(new ImportRowError(row, column, "NOT_A_STUDENT", t + " is not a student account"));
        });
        report.inactive().forEach(t -> errors.add(new ImportRowError(row, column, "STUDENT_INACTIVE", t + " is deactivated")));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Class list template (15 §5.7, D62): row 1 = column names + what to write + what a blank cell means. */
    static byte[] template() {
        return CsvTemplates.csv(List.of(
                "student_code (MSSV — bắt buộc; sinh viên đã có tài khoản chỉ cần cột này)",
                "username (chỉ điền khi sinh viên chưa có tài khoản — tên đăng nhập, duy nhất, 3–50 ký tự a-z 0-9 . - _)",
                "full_name (chỉ điền khi chưa có tài khoản — họ tên)",
                "email (chỉ điền khi chưa có tài khoản — duy nhất, dùng để đặt lại mật khẩu)",
                "password (chỉ dùng khi tạo tài khoản mới, 8–100 ký tự — trống = mật khẩu mặc định ở Cấu hình; phải đổi ở lần đăng nhập đầu)"));
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
