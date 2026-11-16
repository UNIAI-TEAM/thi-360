package vn.unicom.thi360.questionbank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Một tệp tải lên của ngân hàng câu hỏi (spec 4.1 {@code question_asset}). {@code objectKey}
 * là khóa trong object storage (tiền tố {@code questions/}), không bao giờ trả ra API;
 * {@code mime} là loại đã dò từ chữ ký byte, không tin {@code Content-Type} của client.
 */
@Entity
@Table(name = "question_asset", schema = "questionbank")
public class QuestionAsset {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private QuestionAssetKind kind;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime", nullable = false)
    private String mime;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "sha256", nullable = false)
    private byte[] sha256;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QuestionAsset() {
    }

    public QuestionAsset(
            UUID id,
            QuestionAssetKind kind,
            String objectKey,
            String fileName,
            String mime,
            long size,
            byte[] sha256,
            Integer width,
            Integer height,
            UUID uploadedBy,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.kind = Objects.requireNonNull(kind);
        this.objectKey = Objects.requireNonNull(objectKey);
        this.fileName = Objects.requireNonNull(fileName);
        this.mime = Objects.requireNonNull(mime);
        this.size = size;
        this.sha256 = Objects.requireNonNull(sha256);
        this.width = width;
        this.height = height;
        this.uploadedBy = uploadedBy;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public UUID getId() { return id; }
    public QuestionAssetKind getKind() { return kind; }
    public String getObjectKey() { return objectKey; }
    public String getFileName() { return fileName; }
    public String getMime() { return mime; }
    public long getSize() { return size; }
    public byte[] getSha256() { return sha256; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
