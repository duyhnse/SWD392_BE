package swd392.group6.AIVES.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.support.TestUsers.PASSWORD;

/** 14 §3.6 / D38 — one active login per account. */
@IntegrationTest
class SingleSessionIntegrationTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired private JdbcTemplate jdbc;

    private ResultActions login(User user, String device, boolean force) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", CHROME_WINDOWS)
                .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + PASSWORD
                        + "\",\"deviceId\":\"" + device + "\",\"force\":" + force + "}"));
    }

    private String token(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token));
    }

    @Test
    void secondDeviceMustConfirmAndThenTheFirstIsSignedOut() throws Exception {
        User user = users.create(Role.STUDENT);
        String laptop = token(login(user, "laptop", false).andExpect(status().isOk()));

        login(user, "phone", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ACTIVE_ELSEWHERE"))
                .andExpect(jsonPath("$.device").value("Chrome · Windows"))
                .andExpect(jsonPath("$.lastSeenAt").isNotEmpty());
        me(laptop).andExpect(status().isOk()); // refusing changed nothing

        String phone = token(login(user, "phone", true).andExpect(status().isOk()));
        me(phone).andExpect(status().isOk());
        me(laptop).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"))
                .andExpect(jsonPath("$.detail").value(containsString("another device")));
    }

    @Test
    void sameBrowserLogsInAgainWithoutPrompt() throws Exception {
        User user = users.create(Role.LECTURER);
        String first = token(login(user, "laptop", false).andExpect(status().isOk()));
        String second = token(login(user, "laptop", false).andExpect(status().isOk()));

        me(second).andExpect(status().isOk());
        me(first).andExpect(status().isUnauthorized()); // still only one session
    }

    @Test
    void loggingOutLetsAnotherDeviceSignInWithoutPrompt() throws Exception {
        User user = users.create(Role.STUDENT);
        String laptop = token(login(user, "laptop", false).andExpect(status().isOk()));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + laptop))
                .andExpect(status().isNoContent());
        me(laptop).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        login(user, "phone", false).andExpect(status().isOk());
    }

    @Test
    void anIdleSessionElsewhereDoesNotBlockTheLogin() throws Exception {
        User user = users.create(Role.STUDENT);
        String laptop = token(login(user, "laptop", false).andExpect(status().isOk()));
        jdbc.update("update user_sessions set last_seen_at = now() - interval '31 minutes' where user_id = ?", user.getUserId());

        login(user, "phone", false).andExpect(status().isOk());
        me(laptop).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutTokenIsHarmless() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }
}
