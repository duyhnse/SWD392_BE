package swd392.group6.AIVES.user;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.user.AdminUserDtos.ImportReport;
import swd392.group6.AIVES.user.AdminUserDtos.RowError;
import swd392.group6.AIVES.user.UserApi.NewAccount;
import swd392.group6.AIVES.user.UserApi.ProvisionResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Parses the account import CSV (14 §3.4) and turns provisioning results into a row report. */
@Component
class UserCsvImporter {

    static final int MAX_ROWS = 1000;
    private static final Set<String> REQUIRED = Set.of("username", "full_name", "email");

    private final AccountProvisioningService provisioning;

    UserCsvImporter(AccountProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    ImportReport importCsv(InputStream input, boolean allowRoleColumn) {
        List<NewAccount> accounts = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();

        try (Reader reader = new InputStreamReader(skipBom(input), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).setTrim(true).get().parse(reader)) {
            Map<String, Integer> header = lowerCaseHeader(parser.getHeaderMap());
            if (!header.keySet().containsAll(REQUIRED)) {
                throw invalidFile("Header must contain: username, full_name, email (optional: student_code, role)");
            }
            for (CSVRecord record : parser) {
                int row = (int) record.getRecordNumber() + 1; // +1: the header is row 1, as in a spreadsheet
                if (accounts.size() + errors.size() >= MAX_ROWS) {
                    throw invalidFile("A file may contain at most " + MAX_ROWS + " rows");
                }
                Role role = null;
                String roleValue = value(record, header, "role");
                if (allowRoleColumn && roleValue != null && !roleValue.isBlank()) {
                    try {
                        role = Role.valueOf(roleValue.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        errors.add(new RowError(row, "role", "INVALID_ROLE", "Role must be ADMIN, LECTURER or STUDENT"));
                        continue;
                    }
                }
                accounts.add(new NewAccount(value(record, header, "username"), value(record, header, "full_name"),
                        value(record, header, "email"), value(record, header, "student_code"), role));
                rowNumbers.add(row);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw invalidFile("The file is not a readable UTF-8 CSV");
        }

        int total = accounts.size() + errors.size(); // rows rejected before provisioning (bad role) count too
        List<ProvisionResult> results = provisioning.provision(accounts);
        int created = 0;
        int existing = 0;
        for (int i = 0; i < results.size(); i++) {
            ProvisionResult result = results.get(i);
            if (!result.ok()) {
                errors.add(new RowError(rowNumbers.get(i), result.errorField(), result.errorCode(), result.message()));
            } else if (result.created()) {
                created++;
            } else {
                existing++;
            }
        }
        errors.sort((a, b) -> Integer.compare(a.row(), b.row()));
        return new ImportReport(total, created, existing, errors);
    }

    private static Map<String, Integer> lowerCaseHeader(Map<String, Integer> header) {
        Map<String, Integer> lower = new java.util.HashMap<>();
        header.forEach((name, index) -> lower.put(name.trim().toLowerCase(Locale.ROOT), index));
        return lower;
    }

    private static String value(CSVRecord record, Map<String, Integer> header, String column) {
        Integer index = header.get(column);
        return index == null || index >= record.size() ? null : record.get(index);
    }

    private static InputStream skipBom(InputStream input) throws IOException {
        var buffered = new java.io.BufferedInputStream(input);
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
