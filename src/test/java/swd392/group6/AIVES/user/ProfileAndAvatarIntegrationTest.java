package swd392.group6.AIVES.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.support.IntegrationTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.user.CrudFixtures.unique;

/** 15 §5.1 self-service: PATCH /users/me, avatars (D33, AC-C10), Google unlink (D34). */
@IntegrationTest
class ProfileAndAvatarIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StoragePort storage;

    private User me;
    private String auth;

    @BeforeEach
    void setUp() throws Exception {
        CrudFixtures fx = new CrudFixtures(jdbc, userRepository, passwordEncoder);
        me = fx.user(Role.STUDENT, "me" + unique());
        auth = CrudFixtures.bearer(mockMvc, me);
    }

    private static MockMultipartHttpServletRequestBuilder putAvatar(byte[] data, String contentType) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/users/me/avatar");
        builder.file(new MockMultipartFile("file", "pic", contentType, data));
        builder.with(r -> {
            r.setMethod("PUT");
            return r;
        });
        return builder;
    }

    // --- PATCH /users/me ----------------------------------------------------------------------------

    @Test
    void userChangesOnlyPreferredLanguage() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredLanguage\":\"EN\",\"fullName\":\"Hacker\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("EN"))
                .andExpect(jsonPath("$.fullName").value(me.getFullName()))
                .andExpect(jsonPath("$.role").value("STUDENT"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", auth))
                .andExpect(jsonPath("$.preferredLanguage").value("EN"));
    }

    @Test
    void invalidLanguageIs400AndAnonymousIs401() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredLanguage\":\"FR\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"preferredLanguage\":\"EN\"}"))
                .andExpect(status().isUnauthorized());
    }

    // --- avatars (D33, D37: every picture becomes a centre-cropped square JPEG ≤ 512 px) -----------------

    @Test
    void uploadedAvatarIsCroppedResizedAndServedPublicly_AC_C10() throws Exception {
        mockMvc.perform(putAvatar(CrudFixtures.png(1200, 600), "application/octet-stream").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(startsWith("/api/v1/avatars/" + me.getUserId() + "?v=")));

        // No Authorization header: avatars are public (D33).
        byte[] served = mockMvc.perform(get("/api/v1/avatars/" + me.getUserId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andReturn().getResponse().getContentAsByteArray();
        java.awt.image.BufferedImage img = CrudFixtures.decode(served);
        assertThat(img.getWidth()).isEqualTo(512);
        assertThat(img.getHeight()).isEqualTo(512);
        // 1200x600 red|green|blue bands: the centred 600x600 square is all green, so are its corners.
        assertGreen(img.getRGB(5, 5));
        assertGreen(img.getRGB(506, 506));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", auth))
                .andExpect(jsonPath("$.avatarSource").value("UPLOAD"))
                .andExpect(jsonPath("$.avatarUrl").value(startsWith("/api/v1/avatars/" + me.getUserId() + "?v=")));
        String key = userRepository.findById(me.getUserId()).orElseThrow().getAvatarKey();
        assertThat(key).startsWith("avatars/" + me.getUserId() + "/").endsWith(".jpg");
    }

    @Test
    void smallPicturesAreNotUpscaled() throws Exception {
        mockMvc.perform(putAvatar(CrudFixtures.png(120, 80), "image/png").header("Authorization", auth))
                .andExpect(status().isOk());
        java.awt.image.BufferedImage img = CrudFixtures.decode(
                mockMvc.perform(get("/api/v1/avatars/" + me.getUserId())).andReturn().getResponse().getContentAsByteArray());
        assertThat(img.getWidth()).isEqualTo(80);
        assertThat(img.getHeight()).isEqualTo(80);
    }

    @Test
    void jpegWebpGifAndBmpAreAcceptedAndReplacingDeletesTheOldObject() throws Exception {
        mockMvc.perform(putAvatar(CrudFixtures.image("jpg", 300, 300), "image/jpeg").header("Authorization", auth))
                .andExpect(status().isOk());
        String first = userRepository.findById(me.getUserId()).orElseThrow().getAvatarKey();
        assertThat(storage.exists(first)).isTrue();

        mockMvc.perform(putAvatar(CrudFixtures.webp(), "image/webp").header("Authorization", auth))
                .andExpect(status().isOk());
        String second = userRepository.findById(me.getUserId()).orElseThrow().getAvatarKey();
        assertThat(second).endsWith(".jpg").isNotEqualTo(first);
        assertThat(storage.exists(first)).isFalse();

        mockMvc.perform(putAvatar(CrudFixtures.image("gif", 64, 64), "image/gif").header("Authorization", auth))
                .andExpect(status().isOk());
        mockMvc.perform(putAvatar(CrudFixtures.image("bmp", 64, 64), "image/bmp").header("Authorization", auth))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/avatars/" + me.getUserId())).andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void oversizedFilesAndHugeDimensionsAreRejected_AC_C10() throws Exception {
        mockMvc.perform(putAvatar(CrudFixtures.fakePng(10 * 1024 * 1024 + 1), "image/png").header("Authorization", auth))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("AVATAR_TOO_LARGE"));
        // A tiny file that claims 8001 px is refused before its pixels are decoded (decompression bomb).
        mockMvc.perform(putAvatar(CrudFixtures.png(8001, 1), "image/png").header("Authorization", auth))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AVATAR_DIMENSIONS"));
    }

    @Test
    void nonImageIsRejectedEvenWithImageContentType_AC_C10() throws Exception {
        mockMvc.perform(putAvatar("GIF89a....".getBytes(), "image/png").header("Authorization", auth))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("AVATAR_UNSUPPORTED_TYPE"));
        mockMvc.perform(putAvatar("%PDF-1.7".getBytes(), "application/pdf").header("Authorization", auth))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("AVATAR_UNSUPPORTED_TYPE"));
        mockMvc.perform(putAvatar(new byte[0], "image/png").header("Authorization", auth))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AVATAR_EMPTY"));
        assertThat(userRepository.findById(me.getUserId()).orElseThrow().getAvatarKey()).isNull();
    }

    @Test
    void deletingAvatarMakesPublicFetch404() throws Exception {
        mockMvc.perform(putAvatar(CrudFixtures.png(64, 64), "image/png").header("Authorization", auth))
                .andExpect(status().isOk());
        String key = userRepository.findById(me.getUserId()).orElseThrow().getAvatarKey();

        mockMvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", auth))
                .andExpect(status().isNoContent());
        assertThat(storage.exists(key)).isFalse();
        mockMvc.perform(get("/api/v1/avatars/" + me.getUserId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AVATAR_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", auth))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.avatarSource").doesNotExist());
    }

    private static void assertGreen(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        assertThat(g).as("green channel").isGreaterThan(200);
        assertThat(r).as("red channel").isLessThan(60);
        assertThat(b).as("blue channel").isLessThan(60);
    }

    @Test
    void avatarOfUnknownUserIs404AndUploadNeedsAuth() throws Exception {
        mockMvc.perform(get("/api/v1/avatars/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AVATAR_NOT_FOUND"));
        mockMvc.perform(putAvatar(CrudFixtures.png(64, 64), "image/png")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/users/me/avatar")).andExpect(status().isUnauthorized());
    }

    // --- DELETE /users/me/google ----------------------------------------------------------------------

    @Test
    void unlinkGoogleClearsSubjectEmailAndLinkTime() throws Exception {
        User user = userRepository.findById(me.getUserId()).orElseThrow();
        user.setGoogleSubject("google-sub-" + unique());
        user.setGoogleEmail("me@gmail.com");
        user.setGoogleLinkedAt(Instant.parse("2026-10-01T00:00:00Z"));
        userRepository.save(user);

        String json = mockMvc.perform(get("/api/v1/users/me").header("Authorization", auth))
                .andExpect(jsonPath("$.googleLinked").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(json, "$.googleEmail")).isEqualTo("me@gmail.com");

        mockMvc.perform(delete("/api/v1/users/me/google").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleLinked").value(false))
                .andExpect(jsonPath("$.googleEmail").doesNotExist());
        User after = userRepository.findById(me.getUserId()).orElseThrow();
        assertThat(after.getGoogleSubject()).isNull();
        assertThat(after.getGoogleEmail()).isNull();
        assertThat(after.getGoogleLinkedAt()).isNull();
        mockMvc.perform(delete("/api/v1/users/me/google")).andExpect(status().isUnauthorized());
    }
}
