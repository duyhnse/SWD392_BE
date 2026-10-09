package swd392.group6.AIVES.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.UserApi.NewAccount;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.support.TestUsers.PASSWORD;

/** 14_AUTH_AND_ACCOUNTS.md §3.4 — provisioning by admins and class lists. */
@IntegrationTest
class AdminUsersIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private UserApi userApi;
    @Autowired private UserRepository userRepository;

    private String adminToken() throws Exception {
        return TestUsers.login(mockMvc, users.create(Role.ADMIN).getUsername(), PASSWORD);
    }

    private static String code() {
        return "SE" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    @Test
    void studentsNeedAStudentCode_D40() throws Exception {
        String u = unique();
        var result = userApi.ensureStudents(List.of(NewAccount.student(u, "Không Mã", u + "@fpt.edu.vn", " ")));
        assertThat(result.getFirst().errorCode()).isEqualTo("STUDENT_CODE_REQUIRED");
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"fullName\":\"X\",\"email\":\"" + u + "@a.vn\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STUDENT_CODE_REQUIRED"));
        // A lecturer never has one: a code sent for a lecturer is ignored.
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"fullName\":\"X\",\"email\":\"" + u + "@a.vn\",\"role\":\"LECTURER\",\"studentCode\":\"SE111\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentCode").doesNotExist());
    }

    private static String unique() {
        return "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    void adminCreatesAccountThatCannotLogInUntilActivated() throws Exception {
        String username = unique();
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username.toUpperCase() + "\",\"fullName\":\"Lan\",\"email\":\""
                                + username + "@fpt.edu.vn\",\"role\":\"LECTURER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("LECTURER"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameIsConflict() throws Exception {
        User existing = users.create(Role.STUDENT);
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + existing.getUsername() + "\",\"fullName\":\"X\",\"email\":\"x"
                                + unique() + "@a.vn\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void importReportsCreatedExistingAndRowErrors_AC_A8() throws Exception {
        User existing = users.create(Role.STUDENT);
        String a = unique(), b = unique(), c = unique();
        String csv = "username,full_name,email,student_code\n"
                + a + ",Nguyễn Văn A," + a + "@fpt.edu.vn," + code() + "\n"
                + b + ",Trần Thị B," + b + "@fpt.edu.vn," + code() + "\n"
                + existing.getUsername() + "," + existing.getFullName() + "," + existing.getEmail() + "," + existing.getStudentCode() + "\n"
                + "bad," + "No Email,not-an-email," + code() + "\n"
                + c + ",Lê C," + c + "@fpt.edu.vn," + code() + "\n";

        mockMvc.perform(multipart("/api/v1/admin/users/import")
                        .file(new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.existing").value(1))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(5))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_EMAIL"));
        assertThat(userRepository.findByUsername(a)).map(User::getFullName).contains("Nguyễn Văn A");
    }

    @Test
    void importWithoutRequiredHeaderIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/users/import")
                        .file(new MockMultipartFile("file", "users.csv", "text/csv", "name,mail\nx,y\n".getBytes()))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMPORT_FILE_INVALID"));
    }

    @Test
    void nonAdminsCannotManageUsers() throws Exception {
        String lecturer = TestUsers.login(mockMvc, users.create(Role.LECTURER).getUsername(), PASSWORD);
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + lecturer))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSearchesAndDeactivates() throws Exception {
        User student = users.create(Role.STUDENT);
        String admin = adminToken();

        mockMvc.perform(get("/api/v1/admin/users").param("q", student.getUsername()).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value(student.getUsername()));

        mockMvc.perform(patch("/api/v1/admin/users/" + student.getUserId()).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(student.getUserId())).map(User::isActive).contains(false);
    }

    @Test
    void ensureStudentsIsIdempotent_AC_A9() {
        String u = unique();
        List<NewAccount> list = List.of(NewAccount.student(u, "Phạm D", u + "@fpt.edu.vn", code()));

        var first = userApi.ensureStudents(list);
        var second = userApi.ensureStudents(list);

        assertThat(first.getFirst().created()).isTrue();
        assertThat(second.getFirst().created()).isFalse();
        assertThat(second.getFirst().userId()).isEqualTo(first.getFirst().userId());
    }
}
