package vn.unicom.thi360.shared.storage;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] content, String contentType) {
        objects.put(key, new StoredObject(Arrays.copyOf(content, content.length), contentType));
    }

    @Override
    public Optional<byte[]> get(String key) {
        StoredObject stored = objects.get(key);
        return stored == null ? Optional.empty() : Optional.of(Arrays.copyOf(stored.bytes(), stored.bytes().length));
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return objects.containsKey(key);
    }

    public Optional<byte[]> rawBytes(String key) {
        return get(key);
    }

    public void clear() {
        objects.clear();
    }

    private record StoredObject(byte[] bytes, String contentType) {
    }
}
