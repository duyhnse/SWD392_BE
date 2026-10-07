package swd392.group6.AIVES.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import swd392.group6.AIVES.support.IntegrationTest;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "S3curePassw0rd";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions signUp(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String signUpBody(String email, String extraJson) {
        return "{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"" + extraJson + "}";
    }

    private String registerAndGetToken(String email) throws Exception {
        String json = signUp(signUpBody(email, "")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void signUpCreatesStudentEvenIfClientAsksForAdmin() throws Exception {
        String email = uniqueEmail();

        signUp(signUpBody(email, ",\"roleId\":1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.roleId").value(Role.STUDENT.getId().intValue()))
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Also confirm what actually reached the database.
        org.junit.jupiter.api.Assertions.assertEquals(
                Role.STUDENT.getId(), userRepository.findByEmail(email).orElseThrow().getRoleId());
    }

    @Test
    void duplicateEmailReturnsConflictWithUniformErrorBody() throws Exception {
        String email = uniqueEmail();
        signUp(signUpBody(email, "")).andExpect(status().isCreated());

        signUp(signUpBody(email.toUpperCase(), ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
    }

    @Test
    void invalidSignUpListsFieldErrors() throws Exception {
        signUp("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("fullName", "email", "password")));
    }

    @Test
    void loginSucceedsWithRightPasswordAndFailsWithWrongOne() throws Exception {
        String email = uniqueEmail();
        registerAndGetToken(email);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
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
        String email = uniqueEmail();
        String token = registerAndGetToken(email);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void userCanReadSelfButNotSomeoneElse() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        String tokenA = registerAndGetToken(emailA);
        registerAndGetToken(emailB);
        UUID idA = userRepository.findByEmail(emailA).orElseThrow().getUserId();
        UUID idB = userRepository.findByEmail(emailB).orElseThrow().getUserId();

        mockMvc.perform(get("/api/v1/users/" + idA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/" + idB).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void deactivatedUserCannotLogInAndTheirTokenStopsWorking() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanReadAnyUser() throws Exception {
        String adminEmail = uniqueEmail();
        String studentEmail = uniqueEmail();
        registerAndGetToken(adminEmail);
        registerAndGetToken(studentEmail);
        // Admins are never self-registered: promote directly in the database, then log in again.
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        admin.setRoleId(Role.ADMIN.getId());
        userRepository.save(admin);
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + adminEmail + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String adminToken = JsonPath.read(json, "$.token");
        UUID studentId = userRepository.findByEmail(studentEmail).orElseThrow().getUserId();

        mockMvc.perform(get("/api/v1/users/" + studentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(studentEmail));
    }
}
