package swd392.group6.AIVES.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.support.TestUsers.PASSWORD;

@IntegrationTest
class AuthFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private UserRepository userRepository;

    private static String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void loginWithUsernameInAnyCase_AC_A1() throws Exception {
        User user = users.create(Role.STUDENT);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("  " + user.getUsername().toUpperCase() + " ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(user.getUsername()))
                .andExpect(jsonPath("$.user.role").value("STUDENT"));
    }

    @Test
    void wrongPasswordUnknownUserAndInactiveAccountAllLookTheSame_AC_A1() throws Exception {
        User inactive = users.create(Role.STUDENT);
        inactive.setActive(false);
        userRepository.save(inactive);
        User active = users.create(Role.STUDENT);

        for (String body : new String[]{loginBody(active.getUsername(), "wrong-password"),
                loginBody("nobody-here", PASSWORD), loginBody(inactive.getUsername(), PASSWORD)}) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void selfSignUpNoLongerExists_AC_A2() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\",\"email\":\"x@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void invalidLoginBodyListsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("username", "password")));
    }

    @Test
    void protectedEndpointNeedsAValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheCurrentUser() throws Exception {
        User user = users.create(Role.LECTURER);
        String token = TestUsers.login(mockMvc, user.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.getUsername()))
                .andExpect(jsonPath("$.role").value("LECTURER"));
    }

    @Test
    void userCanReadSelfButNotSomeoneElse_andAdminCanReadAnyone() throws Exception {
        User a = users.create(Role.STUDENT);
        User b = users.create(Role.STUDENT);
        User admin = users.create(Role.ADMIN);
        String tokenA = TestUsers.login(mockMvc, a.getUsername(), PASSWORD);
        String adminToken = TestUsers.login(mockMvc, admin.getUsername(), PASSWORD);

        mockMvc.perform(get("/api/v1/users/" + a.getUserId()).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/" + b.getUserId()).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/users/" + b.getUserId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatedUsersTokenStopsWorking() throws Exception {
        User user = users.create(Role.STUDENT);
        String token = TestUsers.login(mockMvc, user.getUsername(), PASSWORD);
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordChecksCurrentAndLogsOutOtherSessions_AC_A7() throws Exception {
        User user = users.create(Role.STUDENT);
        String oldToken = TestUsers.login(mockMvc, user.getUsername(), PASSWORD);
        Thread.sleep(1100); // JWT "iat" has second precision

        mockMvc.perform(put("/api/v1/users/me/password").header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"not-it\",\"newPassword\":\"BrandNewPass1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));

        String json = mockMvc.perform(put("/api/v1/users/me/password").header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"BrandNewPass1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(json, "$.token");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
        TestUsers.login(mockMvc, user.getUsername(), "BrandNewPass1");
    }
}
