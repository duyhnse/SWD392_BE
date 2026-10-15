package swd392.group6.AIVES.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.support.IntegrationTest;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.user.CrudFixtures.unique;

/** 15 §5.1 own notifications + {@link NotificationApi} for other modules. */
@IntegrationTest
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private NotificationApi notificationApi;

    private CrudFixtures fx;
    private User me;
    private String auth;

    @BeforeEach
    void setUp() throws Exception {
        fx = new CrudFixtures(jdbc, userRepository, passwordEncoder);
        me = fx.user(Role.LECTURER, "me" + unique());
        auth = CrudFixtures.bearer(mockMvc, me);
    }

    @Test
    void listsOwnNotificationsNewestFirstWithPayload() throws Exception {
        UUID first = notificationApi.notify(me.getUserId(), "QUESTIONS_PUBLISHED", "3 câu hỏi đã được công bố", null, null);
        Thread.sleep(5);
        UUID second = notificationApi.notify(me.getUserId(), "GRADING_READY", "Bảng chấm sẵn sàng", "SWD392 – Buổi 1",
                Map.of("evaluationId", "e-1", "count", 2));
        User other = fx.user(Role.LECTURER, "other" + unique());
        notificationApi.notify(other.getUserId(), "GRADING_READY", "not mine", null, null);

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].notificationId").value(second.toString()))
                .andExpect(jsonPath("$.items[0].type").value("GRADING_READY"))
                .andExpect(jsonPath("$.items[0].body").value("SWD392 – Buổi 1"))
                .andExpect(jsonPath("$.items[0].payload.evaluationId").value("e-1"))
                .andExpect(jsonPath("$.items[0].payload.count").value(2))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[1].notificationId").value(first.toString()))
                .andExpect(jsonPath("$.items[1].payload").doesNotExist());

        mockMvc.perform(get("/api/v1/notifications").param("size", "1").param("page", "1").header("Authorization", auth))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].notificationId").value(first.toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void markOneAndAllRead() throws Exception {
        UUID a = notificationApi.notify(me.getUserId(), "X", "a", null, null);
        notificationApi.notify(me.getUserId(), "X", "b", null, null);
        notificationApi.notify(me.getUserId(), "X", "c", null, null);

        mockMvc.perform(post("/api/v1/notifications/" + a + "/read").header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/notifications/" + a + "/read").header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/notifications").param("unreadOnly", "true").header("Authorization", auth))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", auth))
                .andExpect(jsonPath("$.items[?(@.notificationId=='" + a + "')].read").value(true));

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/notifications").param("unreadOnly", "true").header("Authorization", auth))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void cannotReadSomeoneElsesNotification() throws Exception {
        User other = fx.user(Role.STUDENT, "other" + unique());
        UUID theirs = notificationApi.notify(other.getUserId(), "X", "theirs", null, null);

        mockMvc.perform(post("/api/v1/notifications/" + theirs + "/read").header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", auth))
                .andExpect(status().isNoContent());
        String otherAuth = CrudFixtures.bearer(mockMvc, other);
        mockMvc.perform(get("/api/v1/notifications").param("unreadOnly", "true").header("Authorization", otherAuth))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void anonymousIs401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/notifications/" + UUID.randomUUID() + "/read")).andExpect(status().isUnauthorized());
    }
}
