package swd392.group6.AIVES.user;

import java.util.Map;
import java.util.UUID;

/**
 * Public API of the user module: in-app notifications for other modules (e.g. QUESTIONS_PUBLISHED,
 * GRADING_READY, SESSION_INTERRUPTED). Users read them through {@code GET /notifications}.
 */
public interface NotificationApi {

    /**
     * Stores a notification for one user and returns its id.
     *
     * @param type    UPPER_SNAKE event type, at most 50 characters
     * @param title   short text, at most 200 characters
     * @param body    optional longer text
     * @param payload optional JSON-serialisable data (e.g. ids for a deep link); may be null
     */
    UUID notify(UUID userId, String type, String title, String body, Map<String, Object> payload);
}
