package swd392.group6.AIVES.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.RecordingMailPort;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.user.CrudFixtures.unique;

/** 15_CRUD_CATALOGUE.md §5.1 — account administration endpoints added in the CRUD milestone. */
@IntegrationTest
class AdminUserManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RecordingMailPort mail;

    private CrudFixtures fx;
    private User admin;
    private String adminAuth;

    @BeforeEach
    void setUp() throws Exception {
        fx = new CrudFixtures(jdbc, userRepository, passwordEncoder);
        admin = fx.user(Role.ADMIN, "adm" + unique());
        adminAuth = CrudFixtures.bearer(mockMvc, admin);
    }

    // --- GET /admin/users/{id} --------------------------------------------------------------------

    @Test
    void lecturerDetailListsAssignedCourses() throws Exception {
        User lecturer = fx.user(Role.LECTURER, "lec" + unique());
        UUID course = fx.course("D" + unique().toUpperCase());
        fx.assign(course, lecturer.getUserId());

        mockMvc.perform(get("/api/v1/admin/users/" + lecturer.getUserId()).header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(lecturer.getUserId().toString()))
                .andExpect(jsonPath("$.username").value(lecturer.getUsername()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].courseId").value(course.toString()))
                .andExpect(jsonPath("$.examSessionCount").doesNotExist());
    }

    @Test
    void studentDetailCountsExamSessions() throws Exception {
        User lecturer = fx.user(Role.LECTURER, "lec" + unique());
        User student = fx.user(Role.STUDENT, "stu" + unique());
        UUID course = fx.course("S" + unique().toUpperCase());
        fx.examSession(course, lecturer.getUserId(), student.getUserId());

        mockMvc.perform(get("/api/v1/admin/users/" + student.getUserId()).header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.examSessionCount").value(1))
                .andExpect(jsonPath("$.courses").doesNotExist());
    }

    @Test
    void unknownUserIs404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID()).header("Authorization", adminAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // --- GET /admin/users?active= -----------------------------------------------------------------

    @Test
    void listFiltersByActive() throws Exception {
        String prefix = "act" + unique();
        fx.user(Role.STUDENT, prefix + "a");
        User inactive = fx.user(Role.STUDENT, prefix + "b");
        inactive.setActive(false);
        userRepository.save(inactive);

        mockMvc.perform(get("/api/v1/admin/users").param("q", prefix).header("Authorization", adminAuth))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/api/v1/admin/users").param("q", prefix).param("active", "false").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value(inactive.getUsername()))
                .andExpect(jsonPath("$.items[0].active").value(false));
        mockMvc.perform(get("/api/v1/admin/users").param("q", prefix).param("active", "true").param("role", "STUDENT")
                        .header("Authorization", adminAuth))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void listsWorkWithoutSearchText() throws Exception {
        fx.user(Role.LECTURER, "lec" + unique());
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty());
        mockMvc.perform(get("/api/v1/admin/lecturers").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty());
    }

    // --- PATCH /admin/users/{id} ------------------------------------------------------------------

    @Test
    void adminChangesRoleAndLecturerLosesAssignments() throws Exception {
        User lecturer = fx.user(Role.LECTURER, "lec" + unique());
        UUID course = fx.course("R" + unique().toUpperCase());
        fx.assign(course, lecturer.getUserId());

        mockMvc.perform(patch("/api/v1/admin/users/" + lecturer.getUserId()).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\",\"fullName\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.fullName").value("Renamed"));
        assertThat(jdbc.queryForObject("select count(*) from course_lecturers where lecturer_id = ?", Integer.class,
                lecturer.getUserId())).isZero();
    }

    @Test
    void adminCannotChangeOwnRoleOrDeactivateSelf() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + admin.getUserId()).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"LECTURER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF"));
        mockMvc.perform(patch("/api/v1/admin/users/" + admin.getUserId()).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF"));
        // Editing one's own name (and re-sending the same role) is fine.
        mockMvc.perform(patch("/api/v1/admin/users/" + admin.getUserId()).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"fullName\":\"Me\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Me"));
    }

    @Test
    void patchValidatesAndDetectsDuplicates() throws Exception {
        User a = fx.user(Role.STUDENT, "dup" + unique());
        User b = fx.user(Role.STUDENT, "dup" + unique());
        mockMvc.perform(patch("/api/v1/admin/users/" + a.getUserId()).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + b.getEmail() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        mockMvc.perform(patch("/api/v1/admin/users/" + a.getUserId()).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(patch("/api/v1/admin/users/" + a.getUserId()).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- DELETE /admin/users/{id} -----------------------------------------------------------------

    @Test
    void userWithoutDataIsDeleted() throws Exception {
        User student = fx.user(Role.STUDENT, "del" + unique());
        jdbc.update("insert into notifications (notification_id, user_id, type, title) values (?, ?, 'X', 'x')",
                UUID.randomUUID(), student.getUserId());

        mockMvc.perform(delete("/api/v1/admin/users/" + student.getUserId()).header("Authorization", adminAuth))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(student.getUserId())).isEmpty();
    }

    @Test
    void referencedUserIsInUse_AC_C11() throws Exception {
        User lecturer = fx.user(Role.LECTURER, "lec" + unique());
        User student = fx.user(Role.STUDENT, "stu" + unique());
        UUID course = fx.course("U" + unique().toUpperCase());
        fx.assign(course, lecturer.getUserId());
        fx.examSession(course, lecturer.getUserId(), student.getUserId());

        mockMvc.perform(delete("/api/v1/admin/users/" + student.getUserId()).header("Authorization", adminAuth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_IN_USE"));
        mockMvc.perform(delete("/api/v1/admin/users/" + lecturer.getUserId()).header("Authorization", adminAuth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_IN_USE"));
        // Nothing was half-deleted: the assignment survived the rolled-back attempt.
        assertThat(jdbc.queryForObject("select count(*) from course_lecturers where lecturer_id = ?", Integer.class,
                lecturer.getUserId())).isEqualTo(1);
        assertThat(userRepository.findById(student.getUserId())).isPresent();
    }

    @Test
    void adminCannotDeleteSelfAndUnknownIs404() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/" + admin.getUserId()).header("Authorization", adminAuth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF"));
        mockMvc.perform(delete("/api/v1/admin/users/" + UUID.randomUUID()).header("Authorization", adminAuth))
                .andExpect(status().isNotFound());
    }

    // --- POST /admin/users/{id}/password-reset ----------------------------------------------------

    @Test
    void adminSendsResetLink() throws Exception {
        User student = fx.user(Role.STUDENT, "rst" + unique());
        mockMvc.perform(post("/api/v1/admin/users/" + student.getUserId() + "/password-reset").header("Authorization", adminAuth))
                .andExpect(status().isAccepted());
        assertThat(mail.sentTo(student.getEmail())).hasSize(1);
        assertThat(mail.sentTo(student.getEmail()).getFirst().text()).contains("/reset-password?token=");
    }

    @Test
    void resetLinkForInactiveOrUnknownUserIsRefused() throws Exception {
        User student = fx.user(Role.STUDENT, "rst" + unique());
        student.setActive(false);
        userRepository.save(student);
        mockMvc.perform(post("/api/v1/admin/users/" + student.getUserId() + "/password-reset").header("Authorization", adminAuth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_INACTIVE"));
        assertThat(mail.sentTo(student.getEmail())).isEmpty();
        mockMvc.perform(post("/api/v1/admin/users/" + UUID.randomUUID() + "/password-reset").header("Authorization", adminAuth))
                .andExpect(status().isNotFound());
    }

    // --- PUT / DELETE /admin/users/{id}/avatar ----------------------------------------------------

    @Test
    void adminSetsAndRemovesSomeonesAvatar() throws Exception {
        User student = fx.user(Role.STUDENT, "ava" + unique());
        mockMvc.perform(multipart("/api/v1/admin/users/" + student.getUserId() + "/avatar")
                        .file(new MockMultipartFile("file", "a.png", "image/png", CrudFixtures.png(100)))
                        .with(r -> { r.setMethod("PUT"); return r; })
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/avatars/" + student.getUserId() + "?v=")));
        mockMvc.perform(get("/api/v1/avatars/" + student.getUserId())).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/users/" + student.getUserId() + "/avatar").header("Authorization", adminAuth))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/avatars/" + student.getUserId())).andExpect(status().isNotFound());
    }

    // --- GET /admin/lecturers ---------------------------------------------------------------------

    @Test
    void lecturersShortcutListsLecturersWithCourseCount() throws Exception {
        String prefix = "lst" + unique();
        User l1 = fx.user(Role.LECTURER, prefix + "a");
        fx.user(Role.LECTURER, prefix + "b");
        fx.user(Role.STUDENT, prefix + "c");
        fx.assign(fx.course("L" + unique().toUpperCase()), l1.getUserId());
        fx.assign(fx.course("L" + unique().toUpperCase()), l1.getUserId());

        mockMvc.perform(get("/api/v1/admin/lecturers").param("q", prefix).header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[?(@.username=='" + prefix + "a')].courseCount").value(2))
                .andExpect(jsonPath("$.items[?(@.username=='" + prefix + "b')].courseCount").value(0));
    }

    // --- authorization ----------------------------------------------------------------------------

    @Test
    void nonAdminsGet403AndAnonymous401() throws Exception {
        User target = fx.user(Role.STUDENT, "tgt" + unique());
        for (Role role : new Role[]{Role.LECTURER, Role.STUDENT}) {
            String auth = CrudFixtures.bearer(mockMvc, fx.user(role, "x" + unique()));
            mockMvc.perform(get("/api/v1/admin/users/" + target.getUserId()).header("Authorization", auth))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/admin/users/" + target.getUserId()).header("Authorization", auth))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/admin/users/" + target.getUserId() + "/password-reset").header("Authorization", auth))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/admin/users/" + target.getUserId() + "/avatar").header("Authorization", auth))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/lecturers").header("Authorization", auth))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/admin/users/" + target.getUserId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/lecturers")).andExpect(status().isUnauthorized());
        assertThat(userRepository.findById(target.getUserId())).isPresent();
    }
}
