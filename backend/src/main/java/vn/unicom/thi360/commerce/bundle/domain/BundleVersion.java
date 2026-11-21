package vn.unicom.thi360.commerce.bundle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Frozen published snapshot of a bundle ({@code commerce.bundle_version}, TM 16.1). */
@Entity
@Table(name = "bundle_version", schema = "commerce")
public class BundleVersion {

    @Id
    private UUID id;

    @Column(name = "bundle_id", nullable = false, updatable = false)
    private UUID bundleId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    private BundleSnapshot snapshot;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "published_by", updatable = false)
    private UUID publishedBy;

    @Column(name = "published_by_username", updatable = false)
    private String publishedByUsername;

    protected BundleVersion() {
    }

    public BundleVersion(
            UUID id,
            UUID bundleId,
            int versionNo,
            BundleSnapshot snapshot,
            Instant publishedAt,
            UUID publishedBy,
            String publishedByUsername) {
        this.id = id;
        this.bundleId = bundleId;
        this.versionNo = versionNo;
        this.snapshot = snapshot;
        this.publishedAt = publishedAt;
        this.publishedBy = publishedBy;
        this.publishedByUsername = publishedByUsername;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public BundleSnapshot getSnapshot() {
        return snapshot;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public String getPublishedByUsername() {
        return publishedByUsername;
    }
}
