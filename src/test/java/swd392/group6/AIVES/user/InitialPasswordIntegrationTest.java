package swd392.group6.AIVES.user;

import swd392.group6.AIVES.user.repository.UserRepository;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.common.CsvTemplates;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.support.TestUsers.PASSWORD;

/** D62: initial passwords (given or the default setting) and the forced change at the first login. */
@IntegrationTest
class InitialPasswordIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private UserRepository userRepository;

    private String admin() throws Exception {
        return "Bearer " + TestUsers.login(mockMvc, users.create(Role.ADMIN).getUsername(), PASSWORD);
    }

    private static String unique() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String code() {
        return "SE" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String loginJson(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"deviceId\":\"d\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void adminCreatedAccountUsesTheDefaultPasswordAndMustChangeItFirst() throws Exception {
        String admin = admin();
        mockMvc.perform(put("/api/v1/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":{\"account.default_password\":\"short\"}}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(put("/api/v1/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":{\"account.default_password\":\"Welcome@2026\"}}"))
                .andExpect(status().isOk());
        String u = unique();
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"fullName\":\"Lec\",\"email\":\"" + u + "@fe.edu.vn\",\"role\":\"LECTURER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        String json = loginJson(u, "Welcome@2026");
        assertThat((Boolean) JsonPath.read(json, "$.user.mustChangePassword")).isTrue();
        String token = "Bearer " + JsonPath.read(json, "$.token");
        mockMvc.perform(get("/api/v1/courses").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token)).andExpect(status().isOk());

        String changed = mockMvc.perform(put("/api/v1/users/me/password").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Welcome@2026\",\"newPassword\":\"MyOwn@Pass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/v1/courses").header("Authorization", "Bearer " + JsonPath.read(changed, "$.token")))
                .andExpect(status().isOk());
        assertThat(userRepository.findByUsername(u)).get().extracting(User::isMustChangePassword).isEqualTo(false);
        mockMvc.perform(put("/api/v1/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":{\"account.default_password\":\"Aives@2026\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void accountCsvCoversEveryAttribute_withTheGuidanceHeader() throws Exception {
        String admin = admin();
        byte[] template = mockMvc.perform(get("/api/v1/admin/users/import-template").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String header = new String(template, StandardCharsets.UTF_8);
        assertThat(header).contains("role (STUDENT / LECTURER / ADMIN — trống = STUDENT)").contains("password (").contains("active (");
        String a = unique(), b = unique(), c = unique(), d = unique();
        String csv = header
                + a + ",Sinh viên A," + a + "@fpt.edu.vn,," + code() + ",Chosen@Pass1,\n"
                + b + ",Giảng viên B," + b + "@fe.edu.vn,lecturer,,,false\n"
                + c + ",Short Pass," + c + "@fpt.edu.vn,STUDENT," + code() + ",abc,\n"
                + d + ",Bad Active," + d + "@fpt.edu.vn,," + code() + ",,maybe\n";
        mockMvc.perform(multipart("/api/v1/admin/users/import").header("Authorization", admin)
                        .file(new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.errors[0].row").value(4))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.errors[1].code").value("INVALID_ACTIVE"));

        assertThat((Boolean) JsonPath.read(loginJson(a, "Chosen@Pass1"), "$.user.mustChangePassword")).isTrue();
        User lecturer = userRepository.findByUsername(b).orElseThrow();
        assertThat(lecturer.getRole()).isEqualTo(Role.LECTURER);
        assertThat(lecturer.isActive()).isFalse();
        assertThat(lecturer.isMustChangePassword()).isTrue();
    }

    @Test
    void headerCellsAreReadByTheirLeadingName() {
        assertThat(CsvTemplates.columnName("﻿username (bắt buộc — …)")).isEqualTo("username");
        assertThat(CsvTemplates.columnName("\"student_code (MSSV)\"")).isEqualTo("student_code");
        assertThat(CsvTemplates.columnName("Email")).isEqualTo("email");
        assertThat(CsvTemplates.columnName("(nothing)")).isEmpty();
        assertThat(CsvTemplates.bool("Không")).isFalse();
        assertThat(CsvTemplates.bool(" ")).isNull();
    }

    @Test
    void aGivenPasswordWorksButMustAlsoBeChanged() throws Exception {
        String admin = admin();
        String u = unique();
        mockMvc.perform(post("/api/v1/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"fullName\":\"S\",\"email\":\"" + u + "@fpt.edu.vn\",\"role\":\"STUDENT\","
                                + "\"studentCode\":\"" + code() + "\",\"password\":\"Given@Pass1\"}"))
                .andExpect(status().isCreated());
        String token = "Bearer " + JsonPath.read(loginJson(u, "Given@Pass1"), "$.token");
        mockMvc.perform(get("/api/v1/me/viva-exams").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("initial password")));
    }
}
