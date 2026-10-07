package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Creates accounts for admins (single/CSV) and lecturers' class lists (14_AUTH_AND_ACCOUNTS.md §3.4). */
@Service
@RequiredArgsConstructor
class AccountProvisioningService implements UserApi {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public List<ProvisionResult> ensureStudents(List<NewAccount> students) {
        return provision(students.stream()
                .map(s -> new NewAccount(s.username(), s.fullName(), s.email(), s.studentCode(), Role.STUDENT))
                .toList());
    }

    /** Creates one account; any problem is a 409/422 for the admin form. */
    @Transactional
    public User create(NewAccount account) {
        // Matching an existing username is right for imports, but a create form must refuse it.
        if (userRepository.existsByUsername(PasswordRules.normalize(account.username()))) {
            throw ApiException.conflict("USERNAME_ALREADY_EXISTS", "Username is already taken");
        }
        ProvisionResult result = provision(List.of(account)).getFirst();
        if (!result.ok()) {
            throw "USERNAME_ALREADY_EXISTS".equals(result.errorCode())
                    || "EMAIL_ALREADY_REGISTERED".equals(result.errorCode())
                    || "STUDENT_CODE_ALREADY_EXISTS".equals(result.errorCode())
                    ? ApiException.conflict(result.errorCode(), result.message())
                    : ApiException.unprocessable(result.errorCode(), result.message());
        }
        return userRepository.findById(result.userId()).orElseThrow();
    }

    /**
     * Validates every row, matches existing usernames (never overwrites them) and creates the rest.
     * Rows are independent: a bad row does not stop the others.
     */
    @Transactional
    public List<ProvisionResult> provision(List<NewAccount> accounts) {
        List<ProvisionResult> results = new ArrayList<>(accounts.size());
        Set<String> seenUsernames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenCodes = new HashSet<>();
        for (NewAccount raw : accounts) {
            results.add(provisionOne(raw, seenUsernames, seenEmails, seenCodes));
        }
        return results;
    }

    private ProvisionResult provisionOne(NewAccount raw, Set<String> seenUsernames, Set<String> seenEmails, Set<String> seenCodes) {
        String username = PasswordRules.normalize(raw.username());
        String email = PasswordRules.normalize(raw.email());
        String fullName = raw.fullName() == null ? "" : raw.fullName().trim();
        String code = raw.studentCode() == null || raw.studentCode().isBlank() ? null : raw.studentCode().trim().toUpperCase();
        Role role = raw.role() == null ? Role.STUDENT : raw.role();

        if (username == null || !PasswordRules.USERNAME.matcher(username).matches()) {
            return error("INVALID_USERNAME", "username", "Username must be 3-50 characters: a-z, 0-9, dot, dash, underscore");
        }
        if (!seenUsernames.add(username)) {
            return error("DUPLICATE_ROW", "username", "Username appears more than once in this list");
        }
        Optional<User> existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            User user = existing.get();
            if (email != null && !email.isEmpty() && !email.equals(user.getEmail())) {
                return error("EMAIL_MISMATCH", "email", "Username exists with a different email");
            }
            if (raw.role() != null && user.getRole() != role) {
                return error("ROLE_MISMATCH", "role", "Username exists with role " + user.getRole());
            }
            return new ProvisionResult(user.getUserId(), false, null, null, null);
        }

        if (fullName.isEmpty() || fullName.length() > 100) {
            return error("INVALID_FULL_NAME", "full_name", "Full name is required (max 100 characters)");
        }
        if (email == null || email.length() > 100 || !EMAIL.matcher(email).matches()) {
            return error("INVALID_EMAIL", "email", "A valid email is required");
        }
        if (!seenEmails.add(email) || userRepository.existsByEmail(email)) {
            return error("EMAIL_ALREADY_REGISTERED", "email", "Email is already used by another account");
        }
        if (code != null && (code.length() > 20 || !seenCodes.add(code) || userRepository.existsByStudentCode(code))) {
            return error("STUDENT_CODE_ALREADY_EXISTS", "student_code", "Student code is invalid or already used");
        }

        User user = userRepository.save(User.builder()
                .username(username)
                .fullName(fullName)
                .email(email)
                .studentCode(code)
                .roleId(role.getId())
                .hashedPassword(passwordEncoder.encode(randomSecret())) // real password lives in the university system; here: Forgot password (§3.4)
                .build());
        return new ProvisionResult(user.getUserId(), true, null, null, null);
    }

    private static ProvisionResult error(String code, String field, String message) {
        return new ProvisionResult(null, false, code, field, message);
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
