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
     * university account sync; they set a password via Forgot password). Idempotent (AC-A9).
     *
     * @return one entry per input row, in order: the student's id or the row's error
     */
    List<ProvisionResult> ensureStudents(List<NewAccount> students);

    record NewAccount(String username, String fullName, String email, String studentCode, Role role) {

        public static NewAccount student(String username, String fullName, String email, String studentCode) {
            return new NewAccount(username, fullName, email, studentCode, Role.STUDENT);
        }
    }

    /** {@code userId} is set on success; otherwise {@code errorCode} + {@code message} explain the problem. */
    record ProvisionResult(UUID userId, boolean created, String errorCode, String errorField, String message) {

        public boolean ok() {
            return userId != null;
        }
    }
}
