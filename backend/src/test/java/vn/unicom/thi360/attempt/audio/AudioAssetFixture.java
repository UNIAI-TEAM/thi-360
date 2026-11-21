package vn.unicom.thi360.attempt.audio;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * Test fixture loading test MP3 resources into {@link ObjectStorage} and
 * {@code questionbank.question_asset} under fresh UUIDs without touching existing dev seed assets,
 * cleaning up all created rows and storage objects on {@link #cleanup()}.
 */
public final class AudioAssetFixture implements AutoCloseable {

    private static final String KEY_PREFIX = "questions/test-audio/";
    private static final String RESOURCE_PREFIX = "attempt/audio/";

    private final JdbcTemplate jdbc;
    private final ObjectStorage storage;
    private final List<CreatedAsset> created = new ArrayList<>();

    public record CreatedAsset(UUID assetId, String objectKey, String fileName, byte[] bytes) {}

    public AudioAssetFixture(JdbcTemplate jdbc, ObjectStorage storage) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.storage = Objects.requireNonNull(storage);
    }

    public CreatedAsset createFromAudioResource(String resourceFileName) {
        return createFromClasspath(RESOURCE_PREFIX + resourceFileName, resourceFileName);
    }

    public CreatedAsset createFromClasspath(String classpathLocation, String fileName) {
        byte[] bytes = readResource(classpathLocation);
        UUID assetId = Ids.newId();
        String objectKey = KEY_PREFIX + assetId;
        String mime = "audio/mpeg";
        byte[] sha256 = sha256(bytes);

        storage.put(objectKey, bytes, mime);
        jdbc.update(
                """
                INSERT INTO questionbank.question_asset
                    (id, kind, object_key, file_name, mime, size, sha256, width, height, uploaded_by, created_at)
                VALUES (?, 'ATTACHMENT', ?, ?, ?, ?, ?, NULL, NULL, NULL, ?)
                """,
                assetId,
                objectKey,
                fileName,
                mime,
                bytes.length,
                sha256,
                Timestamp.from(Instant.now()));

        CreatedAsset asset = new CreatedAsset(assetId, objectKey, fileName, bytes);
        created.add(asset);
        return asset;
    }

    public void deleteObjectOnly(UUID assetId) {
        for (CreatedAsset asset : created) {
            if (asset.assetId().equals(assetId)) {
                storage.delete(asset.objectKey());
                return;
            }
        }
        String key =
                jdbc.queryForObject(
                        "SELECT object_key FROM questionbank.question_asset WHERE id = ?",
                        String.class,
                        assetId);
        if (key != null) {
            storage.delete(key);
        }
    }

    public void cleanup() {
        for (int i = created.size() - 1; i >= 0; i--) {
            CreatedAsset asset = created.get(i);
            storage.delete(asset.objectKey());
            jdbc.update(
                    "DELETE FROM attempt.audio_asset_duration WHERE asset_id = ?",
                    asset.assetId());
            jdbc.update(
                    "DELETE FROM questionbank.question_asset_ref WHERE asset_id = ?",
                    asset.assetId());
            jdbc.update("DELETE FROM questionbank.question_asset WHERE id = ?", asset.assetId());
        }
        created.clear();
    }

    @Override
    public void close() {
        cleanup();
    }

    public static byte[] readResource(String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read test audio resource: " + classpathLocation, ex);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
