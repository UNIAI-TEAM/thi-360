package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ảnh đại diện hiện tại của tài khoản (TM E20). Mỗi tài khoản một dòng;
 * {@code asset_id} là id công khai trong đường {@code /api/v1/avatars/{assetId}},
 * {@code object_key} trỏ vào bucket riêng tư {@code thi360-avatars}.
 */
@Entity
@Table(name = "user_avatar", schema = "identity")
public class UserAvatar {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_id", nullable = false, unique = true)
    private UUID assetId;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAvatar() {
    }

    public UserAvatar(
            UUID userId,
            UUID assetId,
            String objectKey,
            long sizeBytes,
            Instant now) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.assetId = Objects.requireNonNull(assetId, "assetId must not be null");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey must not be null");
        this.sizeBytes = sizeBytes;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
