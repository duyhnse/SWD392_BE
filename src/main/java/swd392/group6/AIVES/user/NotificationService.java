package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.PageResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Own notifications (15 §5.1) and {@link NotificationApi} for other modules. */
@Service
@RequiredArgsConstructor
class NotificationService implements NotificationApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final Clock clock;

    record NotificationResponse(UUID notificationId, String type, String title, String body, JsonNode payload,
                                boolean read, Instant readAt, Instant createdAt) {
    }

    @Override
    @Transactional
    public UUID notify(UUID userId, String type, String title, String body, Map<String, Object> payload) {
        if (userId == null || type == null || type.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("userId, type and title are required");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into notifications (notification_id, user_id, type, title, body, payload, created_at)
                        values (?, ?, ?, ?, ?, cast(? as jsonb), ?)""")
                .params(id, userId, truncate(type, 50), truncate(title, 200), body,
                        payload == null ? null : JSON.writeValueAsString(payload), Timestamp.from(clock.instant()))
                .update();
        return id;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, boolean unreadOnly, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.clamp(size, 1, 100);
        String filter = unreadOnly ? " and read_at is null" : "";
        long total = jdbc.sql("select count(*) from notifications where user_id = ?" + filter)
                .param(userId).query(Long.class).single();
        var items = jdbc.sql("select notification_id, type, title, body, payload::text, read_at, created_at"
                        + " from notifications where user_id = ?" + filter
                        + " order by created_at desc, notification_id desc limit ? offset ?")
                .params(userId, s, (long) p * s)
                .query((rs, i) -> {
                    String payload = rs.getString(5);
                    Timestamp readAt = rs.getTimestamp(6);
                    return new NotificationResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            rs.getString(4), payload == null ? null : JSON.readTree(payload), readAt != null,
                            readAt == null ? null : readAt.toInstant(), rs.getTimestamp(7).toInstant());
                })
                .list();
        return new PageResponse<>(items, p, s, total);
    }

    /** Idempotent; another user's notification is answered with 404. */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        int exists = jdbc.sql("select count(*) from notifications where notification_id = ? and user_id = ?")
                .params(notificationId, userId).query(Integer.class).single();
        if (exists == 0) {
            throw ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found");
        }
        jdbc.sql("update notifications set read_at = ? where notification_id = ? and read_at is null")
                .params(Timestamp.from(clock.instant()), notificationId).update();
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return jdbc.sql("update notifications set read_at = ? where user_id = ? and read_at is null")
                .params(Timestamp.from(clock.instant()), userId).update();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
