package swd392.group6.AIVES.storage;

import java.util.Optional;

/** Keys look like {@code sessions/{sessionId}/turns/3-{turnId}.webm} (03_DATA_MODEL.md §6). */
public interface StoragePort {

    void put(String key, byte[] data, String contentType);

    Optional<StoredObject> get(String key);

    boolean exists(String key);

    void delete(String key);

    /** Rejects absolute paths and ".." so a key can never escape the storage root. */
    static String requireSafeKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..") || key.contains("\\")) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return key;
    }
}
