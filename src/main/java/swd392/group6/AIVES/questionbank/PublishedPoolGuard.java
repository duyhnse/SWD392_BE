package swd392.group6.AIVES.questionbank;

import java.util.List;
import java.util.UUID;

/**
 * Asked before a published question leaves the pool or changes topic / Bloom (unpublish, discard, edit, topic move).
 * Implemented by the exam module: a buổi thi that is published or open draws at check-in, so it must still find a full
 * set afterwards (15 §5.6). Throws {@code 409 QUESTION_NEEDED_BY_EXAM} when it would not.
 */
public interface PublishedPoolGuard {

    /** One question's fate: {@code removed}, or its new topic and Bloom level. */
    record PoolChange(UUID questionId, boolean removed, UUID topicId, BloomLevel bloomLevel) {

        public static PoolChange removed(UUID questionId) {
            return new PoolChange(questionId, true, null, null);
        }

        public static PoolChange changed(UUID questionId, UUID topicId, BloomLevel bloomLevel) {
            return new PoolChange(questionId, false, topicId, bloomLevel);
        }
    }

    void check(UUID courseId, List<PoolChange> changes);
}
