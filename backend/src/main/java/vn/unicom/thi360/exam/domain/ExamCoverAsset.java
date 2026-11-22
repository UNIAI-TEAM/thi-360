package vn.unicom.thi360.exam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One uploaded cover image of an exam. The bytes live in the object store under {@code objectKey}. */
@Entity
@Table(name = "exam_cover_asset", schema = "exam")
public class ExamCoverAsset {
    @Id
    private UUID id;
    @Column(name = "exam_id", nullable = false)
    private UUID examId;
    @Column(name = "object_key", nullable = false)
    private String objectKey;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false)
    private int width;
    @Column(nullable = false)
    private int height;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "orphaned_at")
    private Instant orphanedAt;

    protected ExamCoverAsset() {
    }

    public ExamCoverAsset(UUID id, UUID examId, String objectKey, String contentType, long sizeBytes,
                          int width, int height, Instant now, UUID createdBy) {
        this.id = id;
        this.examId = examId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.createdAt = now;
        this.createdBy = createdBy;
    }

    /** The key prefix of every cover object in the private bucket. */
    public static final String KEY_PREFIX = "exam-covers/";

    public UUID getId() { return id; }
    public UUID getExamId() { return examId; }
    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getOrphanedAt() { return orphanedAt; }
}
