package swd392.group6.AIVES.storage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryStorageAdapter implements StoragePort {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] data, String contentType) {
        objects.put(StoragePort.requireSafeKey(key), new StoredObject(key, data.clone(), contentType));
    }

    @Override
    public Optional<StoredObject> get(String key) {
        return Optional.ofNullable(objects.get(StoragePort.requireSafeKey(key)));
    }

    @Override
    public boolean exists(String key) {
        return objects.containsKey(StoragePort.requireSafeKey(key));
    }

    @Override
    public void delete(String key) {
        objects.remove(StoragePort.requireSafeKey(key));
    }
}
