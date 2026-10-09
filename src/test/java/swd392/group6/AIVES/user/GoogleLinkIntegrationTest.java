package swd392.group6.AIVES.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.support.TestUsers.PASSWORD;

/** 14 §3.5 — link a Google account, sign in with it, unlink. The verifier is faked: tokens are "subject|email|verified". */
@IntegrationTest
@Import(GoogleLinkIntegrationTest.FakeGoogle.class)
class GoogleLinkIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeGoogle {
        @Bean
        @Primary
        GoogleIdentityVerifier fakeGoogleVerifier() {
            return token -> {
                String[] parts = token.split("\\|");
                if (parts.length != 3) {
                    throw ApiException.unauthorized("GOOGLE_TOKEN_INVALID", "bad token");
                }
                return new GoogleIdentityVerifier.GoogleIdentity(parts[0], parts[1], Boolean.parseBoolean(parts[2]));
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;

    private ResultActions link(String jwt, String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/google").header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"" + idToken + "\"}"));
    }

    private ResultActions googleLogin(String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"" + idToken + "\"}"));
    }

    private static String googleId() {
        return "g" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void linkThenSignInWithGoogleThenUnlink() throws Exception {
        User user = users.create(Role.STUDENT);
        String jwt = TestUsers.login(mockMvc, user.getUsername(), PASSWORD);
        String gid = googleId();

        googleLogin(gid + "|me@gmail.com|true").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_NOT_LINKED"));

        link(jwt, gid + "|me@gmail.com|true").andExpect(status().isOk())
                .andExpect(jsonPath("$.googleLinked").value(true))
                .andExpect(jsonPath("$.googleEmail").value("me@gmail.com"));

        googleLogin(gid + "|me@gmail.com|true").andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(user.getUsername()));

        mockMvc.perform(delete("/api/v1/users/me/google").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.googleLinked").value(false));
        googleLogin(gid + "|me@gmail.com|true").andExpect(status().isUnauthorized());
    }

    @Test
    void aGoogleAccountCanBelongToOnlyOneUser() throws Exception {
        String gid = googleId();
        link(TestUsers.login(mockMvc, users.create(Role.STUDENT).getUsername(), PASSWORD), gid + "|a@gmail.com|true")
                .andExpect(status().isOk());

        link(TestUsers.login(mockMvc, users.create(Role.LECTURER).getUsername(), PASSWORD), gid + "|a@gmail.com|true")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOOGLE_ALREADY_LINKED"));
    }

    @Test
    void unverifiedEmailAndBadTokensAreRefused() throws Exception {
        String jwt = TestUsers.login(mockMvc, users.create(Role.STUDENT).getUsername(), PASSWORD);

        link(jwt, googleId() + "|x@gmail.com|false").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("GOOGLE_EMAIL_NOT_VERIFIED"));
        link(jwt, "garbage").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_TOKEN_INVALID"));
        googleLogin("garbage").andExpect(status().isUnauthorized());
    }

    @Test
    void linkingNeedsALoggedInUser() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"x|y|true\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserCannotSignInWithGoogle() throws Exception {
        User user = users.create(Role.STUDENT);
        String gid = googleId();
        link(TestUsers.login(mockMvc, user.getUsername(), PASSWORD), gid + "|d@gmail.com|true").andExpect(status().isOk());
        String admin = TestUsers.login(mockMvc, users.create(Role.ADMIN).getUsername(), PASSWORD);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/users/" + user.getUserId())
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        googleLogin(gid + "|d@gmail.com|true").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
}
