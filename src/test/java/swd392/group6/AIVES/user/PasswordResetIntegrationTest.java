package swd392.group6.AIVES.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.RecordingMailPort;
import swd392.group6.AIVES.support.TestUsers;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.support.TestUsers.PASSWORD;

/** 14_AUTH_AND_ACCOUNTS.md §3.2 — forgot password / first activation. */
@IntegrationTest
class PasswordResetIntegrationTest {

    private static final Pattern LINK = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private RecordingMailPort mail;
    @Autowired private PasswordResetTokenRepository tokenRepository;

    private ResultActions request(String username, String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .content("{\"username\":\"" + username + "\",\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private String tokenFromLastMail(String email) {
        var messages = mail.sentTo(email);
        Matcher m = LINK.matcher(messages.getLast().text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void matchingPairGetsOneMailAndOthersGetNoneWithIdenticalResponse_AC_A3() throws Exception {
        User user = users.create(Role.STUDENT);

        String matching = request(user.getUsername(), user.getEmail().toUpperCase(), "10.0.0.1")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String wrongEmail = request(user.getUsername(), "other@example.com", "10.0.0.1")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        request("nobody-here", user.getEmail(), "10.0.0.1").andExpect(status().isAccepted());

        assertThat(matching).isEqualTo(wrongEmail);
        assertThat(mail.sentTo(user.getEmail())).hasSize(1);
        assertThat(mail.sentTo(user.getEmail()).getFirst().text()).contains(user.getUsername()).containsPattern(LINK);
    }

    @Test
    void linkSetsPasswordOnceAndRevokesOldTokens_AC_A4_AC_A5() throws Exception {
        User user = users.create(Role.STUDENT);
        String oldJwt = TestUsers.login(mockMvc, user.getUsername(), PASSWORD);
        Thread.sleep(1100); // JWT "iat" has second precision
        request(user.getUsername(), user.getEmail(), "10.0.0.2").andExpect(status().isAccepted());
        String token = tokenFromLastMail(user.getEmail());

        confirm(token, "MyNewPassword9").andExpect(status().isNoContent());
        confirm(token, "AnotherPass99").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));

        TestUsers.login(mockMvc, user.getUsername(), "MyNewPassword9");
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + oldJwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredLinkIsRejected_AC_A4() throws Exception {
        User user = users.create(Role.STUDENT);
        request(user.getUsername(), user.getEmail(), "10.0.0.3").andExpect(status().isAccepted());
        String token = tokenFromLastMail(user.getEmail());
        PasswordResetToken stored = tokenRepository.findByTokenHash(PasswordResetService.sha256(token)).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        tokenRepository.save(stored);

        confirm(token, "MyNewPassword9").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void fourthRequestWithin15MinutesSendsNothing_AC_A6() throws Exception {
        User user = users.create(Role.STUDENT);
        for (int i = 0; i < 4; i++) {
            request(user.getUsername(), user.getEmail(), "10.0.0." + (10 + i)).andExpect(status().isAccepted())
                    .andExpect(content().string(""));
        }
        assertThat(mail.sentTo(user.getEmail())).hasSize(3);
    }

    @Test
    void tooShortNewPasswordIsAValidationError() throws Exception {
        confirm("whatever", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
