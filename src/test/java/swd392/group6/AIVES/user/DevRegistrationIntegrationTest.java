package swd392.group6.AIVES.user;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 14 §3.7 / D39 — development-only self registration. */
class DevRegistrationIntegrationTest {

    static String body(String username, String role, String password, String confirm) {
        return "{\"username\":\"" + username + "\",\"fullName\":\"Dev User\",\"email\":\"" + username + "@dev.local\","
                + "\"studentCode\":\"SE" + Math.abs(username.hashCode() % 1000000) + "\",\"role\":\"" + role + "\","
                + "\"password\":\"" + password + "\",\"confirmPassword\":\"" + confirm + "\"}";
    }

    static String unique() {
        return "Dev" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Nested
    @IntegrationTest
    class WhenDisabled {
        @Autowired private MockMvc mockMvc;

        @Test
        void isOffByDefault() throws Exception {
            mockMvc.perform(get("/api/v1/auth/dev-registration")).andExpect(jsonPath("$.enabled").value(false));
            mockMvc.perform(post("/api/v1/auth/dev-registration").contentType(MediaType.APPLICATION_JSON)
                            .content(body(unique(), "STUDENT", "Password123", "Password123")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DEV_REGISTRATION_DISABLED"));
        }
    }

    @Nested
    @IntegrationTest
    @TestPropertySource(properties = "application.dev.self-registration-enabled=true")
    class WhenEnabled {
        @Autowired private MockMvc mockMvc;

        private ResultActions register(String json) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/dev-registration").contentType(MediaType.APPLICATION_JSON).content(json));
        }

        @Test
        void createsALecturerOrStudentThatCanLogInRightAway() throws Exception {
            String username = unique();
            mockMvc.perform(get("/api/v1/auth/dev-registration")).andExpect(jsonPath("$.enabled").value(true));

            register(body(username, "LECTURER", "Password123", "Password123"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value(username.toLowerCase()))
                    .andExpect(jsonPath("$.role").value("LECTURER"))
                    .andExpect(jsonPath("$.studentCode").doesNotExist());

            TestUsers.login(mockMvc, username.toUpperCase(), "Password123"); // any letter case works
        }

        @Test
        void neverAdminAndPasswordsMustMatch() throws Exception {
            register(body(unique(), "ADMIN", "Password123", "Password123"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
            register(body(unique(), "STUDENT", "Password123", "Password124"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
            register(body(unique(), "STUDENT", "short", "short"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void usernameIsUniqueIgnoringCase() throws Exception {
            String username = unique();
            register(body(username, "STUDENT", "Password123", "Password123")).andExpect(status().isCreated());
            register(body(username.toUpperCase(), "STUDENT", "Password123", "Password123").replace("@dev.local", "x@dev.local"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
        }
    }
}
