package swd392.group6.AIVES.user;

import java.util.List;
import java.util.UUID;

/**
 * Public API of the user module for other modules (02 §3). The exam module uses it for class lists:
 * it never writes the users table itself (D26).
 */
public interface UserApi {

    /**
     * Matches students by username and creates the missing ones as STUDENT accounts (stand-in for the
     * university account sync) with the given or the default password, to be changed at the first login (D62).
     * Idempotent (AC-A9).
     *
     * @return one entry per input row, in order: the student's id or the row's error
     */
    List<ProvisionResult> ensureStudents(List<NewAccount> students);

    /**
     * @param password null / blank = the {@code account.default_password} setting
     * @param active   null = active
     */
    record NewAccount(String username, String fullName, String email, String studentCode, Role role, String password,
                      Boolean active) {

        public NewAccount(String username, String fullName, String email, String studentCode, Role role) {
            this(username, fullName, email, studentCode, role, null, null);
        }

        public static NewAccount student(String username, String fullName, String email, String studentCode) {
            return student(username, fullName, email, studentCode, null);
        }

        public static NewAccount student(String username, String fullName, String email, String studentCode, String password) {
            return new NewAccount(username, fullName, email, studentCode, Role.STUDENT, password, null);
        }
    }

    /** {@code userId} is set on success; otherwise {@code errorCode} + {@code message} explain the problem. */
    record ProvisionResult(UUID userId, boolean created, String errorCode, String errorField, String message) {

        public boolean ok() {
            return userId != null;
        }
    }
}
