package swd392.group6.AIVES.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageAdaptersTest {

    @TempDir Path dir;

    @Test
    void localFilesRoundTripAndDelete() {
        StoragePort storage = new LocalFileStorageAdapter(dir);

        storage.put("sessions/s1/turns/1-t1.webm", new byte[]{1, 2, 3}, "audio/webm");

        assertThat(storage.exists("sessions/s1/turns/1-t1.webm")).isTrue();
        StoredObject object = storage.get("sessions/s1/turns/1-t1.webm").orElseThrow();
        assertThat(object.data()).containsExactly(1, 2, 3);
        assertThat(object.contentType()).isEqualTo("audio/webm");
        storage.delete("sessions/s1/turns/1-t1.webm");
        assertThat(storage.get("sessions/s1/turns/1-t1.webm")).isEmpty();
    }

    @Test
    void keysCannotEscapeTheStorageRoot() {
        StoragePort storage = new LocalFileStorageAdapter(dir);

        assertThatThrownBy(() -> storage.put("../evil", new byte[0], "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.put("/etc/passwd", new byte[0], "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inMemoryRoundTrip() {
        StoragePort storage = new InMemoryStorageAdapter();
        storage.put("a/b.txt", "hi".getBytes(), "text/plain");
        assertThat(storage.get("a/b.txt")).map(StoredObject::contentType).contains("text/plain");
    }
}
