package swd392.group6.AIVES.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Stores each object as a file plus a ".type" sidecar holding its content type. */
class LocalFileStorageAdapter implements StoragePort {

    private static final String TYPE_SUFFIX = ".type";

    private final Path root;

    LocalFileStorageAdapter(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, data);
            Files.writeString(typeFile(file), contentType == null ? "application/octet-stream" : contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + key, e);
        }
    }

    @Override
    public Optional<StoredObject> get(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String type = Files.exists(typeFile(file)) ? Files.readString(typeFile(file)) : "application/octet-stream";
            return Optional.of(new StoredObject(key, Files.readAllBytes(file), type));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(String key) {
        Path file = resolve(key);
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(typeFile(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + key, e);
        }
    }

    private Path resolve(String key) {
        Path file = root.resolve(StoragePort.requireSafeKey(key)).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return file;
    }

    private static Path typeFile(Path file) {
        return file.resolveSibling(file.getFileName() + TYPE_SUFFIX);
    }
}
