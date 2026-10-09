package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.user.AdminUserDtos.CourseRef;
import swd392.group6.AIVES.user.AdminUserDtos.LecturerSummary;
import swd392.group6.AIVES.user.AdminUserDtos.UpdateUserRequest;
import swd392.group6.AIVES.user.AdminUserDtos.UserDetail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Account administration beyond provisioning (15_CRUD_CATALOGUE.md §5.1). */
@Service
@RequiredArgsConstructor
class AdminUserService {

    private final UserRepository userRepository;
    private final JdbcClient jdbc;
    private final AvatarService avatarService;
    private final SessionService sessionService;

    @Transactional(readOnly = true)
    public PageResponse<UserResponseDTO> list(String q, Role role, Boolean active, int page, int size) {
        // "" rather than null: an untyped null parameter breaks "like" on PostgreSQL; like '%%' matches everything.
        String query = q == null || q.isBlank() ? "" : PasswordRules.normalize(q);
        var result = userRepository.search(query, role == null ? null : role.getId(), active,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("username")));
        return PageResponse.of(result, UserResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public UserDetail detail(UUID id) {
        User user = load(id);
        List<CourseRef> courses = null;
        Long sessions = null;
        if (user.getRole() == Role.LECTURER) {
            courses = jdbc.sql("""
                            select c.course_id, c.code, c.name, c.is_active
                            from course_lecturers cl join courses c on c.course_id = cl.course_id
                            where cl.lecturer_id = ? order by c.code""")
                    .param(id)
                    .query((rs, i) -> new CourseRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            rs.getBoolean(4)))
                    .list();
        } else if (user.getRole() == Role.STUDENT) {
            sessions = jdbc.sql("select count(*) from exam_sessions where student_id = ?").param(id).query(Long.class).single();
        }
        return new UserDetail(UserResponseDTO.fromEntity(user), courses, sessions);
    }

    /** An admin cannot change their own role or deactivate themselves (409 CANNOT_CHANGE_SELF). */
    @Transactional
    public UserResponseDTO update(UUID id, UpdateUserRequest request, User caller) {
        User user = load(id);
        boolean self = user.getUserId().equals(caller.getUserId());
        if (self && ((request.role() != null && request.role() != user.getRole())
                || Boolean.FALSE.equals(request.active()))) {
            throw cannotChangeSelf("You cannot change your own role or deactivate yourself");
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = PasswordRules.normalize(request.email());
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "Email is already used by another account");
            }
            user.setEmail(email);
        }
        if (request.studentCode() != null) {
            String code = request.studentCode().isBlank() ? null : request.studentCode().trim().toUpperCase();
            if (code != null && !code.equals(user.getStudentCode()) && userRepository.existsByStudentCode(code)) {
                throw ApiException.conflict("STUDENT_CODE_ALREADY_EXISTS", "Student code is already used");
            }
            user.setStudentCode(code);
        }
        if (request.active() != null) {
            user.setActive(request.active());
            if (!request.active()) {
                sessionService.closeAll(user.getUserId(), "DEACTIVATED");
            }
        }
        if (request.role() != null && request.role() != user.getRole()) {
            if (user.getRole() == Role.LECTURER) {
                // Course rights belong to the LECTURER role; a former lecturer keeps no assignments.
                jdbc.sql("delete from course_lecturers where lecturer_id = ?").param(id).update();
            }
            user.setRoleId(request.role().getId());
        }
        return UserResponseDTO.fromEntity(userRepository.saveAndFlush(user));
    }

    /**
     * Hard delete of an account without data. Personal leftovers (reset tokens, notifications, course
     * assignments, avatar) go with it; any other reference (questions, exams, sessions, grades, audit, ...)
     * makes it 409 USER_IN_USE — deactivate instead.
     */
    @Transactional
    public void delete(UUID id, User caller) {
        User user = load(id);
        if (user.getUserId().equals(caller.getUserId())) {
            throw cannotChangeSelf("You cannot delete your own account");
        }
        String avatarKey = user.getAvatarKey();
        try {
            jdbc.sql("delete from password_reset_tokens where user_id = ?").param(id).update();
            jdbc.sql("delete from notifications where user_id = ?").param(id).update();
            jdbc.sql("delete from course_lecturers where lecturer_id = ?").param(id).update();
            jdbc.sql("delete from users where user_id = ?").param(id).update();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("USER_IN_USE", "The account has data in AIVES. Deactivate it instead.");
        }
        avatarService.deleteQuietly(avatarKey);
    }

    @Transactional(readOnly = true)
    public PageResponse<LecturerSummary> lecturers(String q, Boolean active, int page, int size) {
        // "" rather than null: an untyped null parameter breaks "like" on PostgreSQL; like '%%' matches everything.
        String query = q == null || q.isBlank() ? "" : PasswordRules.normalize(q);
        Page<User> result = userRepository.search(query, Role.LECTURER.getId(), active,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("fullName", "username")));
        Map<UUID, Long> counts = new HashMap<>();
        List<UUID> ids = result.getContent().stream().map(User::getUserId).toList();
        if (!ids.isEmpty()) {
            jdbc.sql("select lecturer_id, count(*) from course_lecturers where lecturer_id in (:ids) group by lecturer_id")
                    .param("ids", ids)
                    .query((rs, i) -> counts.put(rs.getObject(1, UUID.class), rs.getLong(2)))
                    .list();
        }
        return PageResponse.of(result, u -> new LecturerSummary(u.getUserId(), u.getUsername(), u.getFullName(),
                u.getEmail(), u.isActive(), UserResponseDTO.avatarUrl(u), counts.getOrDefault(u.getUserId(), 0L)));
    }

    User load(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    private static ApiException cannotChangeSelf(String message) {
        return ApiException.conflict("CANNOT_CHANGE_SELF", message);
    }
}
