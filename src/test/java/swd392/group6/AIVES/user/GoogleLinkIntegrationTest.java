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

/**
 * 14 §3.5 — link a Google account, sign in with it, unlink; D37 — Google photo adoption.
 * The verifier is faked: tokens are "subject|email|verified[|pictureUrl]"; the photo download is faked too.
 */
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
                if (parts.length != 3 && parts.length != 4) {
                    throw ApiException.unauthorized("GOOGLE_TOKEN_INVALID", "bad token");
                }
                return new GoogleIdentityVerifier.GoogleIdentity(parts[0], parts[1], Boolean.parseBoolean(parts[2]),
                        parts.length == 4 ? parts[3] : null);
            };
        }

        @Bean
        @Primary
        GoogleAvatarFetcher fakeGooglePhotoDownload() {
            return new GoogleAvatarFetcher() {
                @Override
                java.util.Optional<byte[]> fetch(String pictureUrl) {
                    return java.util.Optional.of(CrudFixtures.png(256, 256));
                }
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private UserRepository userRepository;

    private ResultActions link(String jwt, String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/google").header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"" + idToken + "\"}"));
    }

    private ResultActions googleLogin(String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"" + idToken + "\",\"deviceId\":\"" + TestUsers.DEVICE + "\"}"));
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

        String googleJwt = com.jayway.jsonpath.JsonPath.read(googleLogin(gid + "|me@gmail.com|true").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(user.getUsername()))
                .andReturn().getResponse().getContentAsString(), "$.token");
        // One session per account (D38): the Google sign-in replaced the password session.
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/users/me/google").header("Authorization", "Bearer " + googleJwt))
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

    @Test
    void googlePhotoIsAdoptedWhenThereIsNoPictureAndRemovedOnUnlink_D37() throws Exception {
        User user = users.create(Role.STUDENT);
        String jwt = TestUsers.login(mockMvc, user.getUsername(), PASSWORD);

        link(jwt, googleId() + "|p@gmail.com|true|https://lh3.googleusercontent.com/a/photo=s96-c")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarSource").value("GOOGLE"))
                .andExpect(jsonPath("$.avatarUrl").isNotEmpty());
        mockMvc.perform(get("/api/v1/avatars/" + user.getUserId())).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/users/me/google").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
        mockMvc.perform(get("/api/v1/avatars/" + user.getUserId())).andExpect(status().isNotFound());
    }

    @Test
    void ownPictureIsNeverReplacedOrRemovedByGoogle_D37() throws Exception {
        User user = users.create(Role.STUDENT);
        String jwt = TestUsers.login(mockMvc, user.getUsername(), PASSWORD);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/users/me/avatar")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "me.png", "image/png", CrudFixtures.png(300, 300)))
                        .with(r -> { r.setMethod("PUT"); return r; })
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
        String ownKey = userRepository.findById(user.getUserId()).orElseThrow().getAvatarKey();

        link(jwt, googleId() + "|q@gmail.com|true|https://lh3.googleusercontent.com/a/photo=s96-c")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarSource").value("UPLOAD"));
        mockMvc.perform(delete("/api/v1/users/me/google").header("Authorization", "Bearer " + jwt))
                .andExpect(jsonPath("$.avatarSource").value("UPLOAD"));
        org.assertj.core.api.Assertions.assertThat(userRepository.findById(user.getUserId()).orElseThrow().getAvatarKey())
                .isEqualTo(ownKey);
    }
}
