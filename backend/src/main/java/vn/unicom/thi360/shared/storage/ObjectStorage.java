package vn.unicom.thi360.shared.storage;

import java.util.Optional;

public interface ObjectStorage {

    void put(String key, byte[] content, String contentType);

    Optional<byte[]> get(String key);

    void delete(String key);

    boolean exists(String key);
}
