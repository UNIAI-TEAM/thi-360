package vn.unicom.thi360.questionbank.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.unicom.thi360.questionbank.domain.importing.ImportColumn;
import vn.unicom.thi360.questionbank.domain.importing.ImportFileType;

/**
 * Tệp tải lên chờ nhập (spec 4.1, 4.5): {@code UPLOADED} → {@code COMMITTING} →
 * {@code COMMITTED}; dòng bị khóa {@code FOR UPDATE} trong transaction commit nên commit thứ
 * hai chờ rồi thấy {@code COMMITTED} (quyết định 8.10). Tệp trên MinIO xóa sau commit; dòng
 * giữ lại kèm {@code importId} để truy vết, hết {@code expiresAt} (24 h) thì job dọn.
 */
@Entity
@Table(name = "question_import_upload", schema = "questionbank")
public class QuestionImportUpload {

    public enum Status {
        UPLOADED,
        COMMITTING,
        COMMITTED
    }

    @Id
    private UUID id;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private ImportFileType fileType;

    @Column(name = "sheet_name")
    private String sheetName;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<ImportColumn> columns = List.of();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.UPLOADED;

    @Column(name = "import_id")
    private UUID importId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "committed_at")
    private Instant committedAt;

    protected QuestionImportUpload() {}

    public QuestionImportUpload(
            UUID id,
            String objectKey,
            String fileName,
            ImportFileType fileType,
            String sheetName,
            int rowCount,
            List<ImportColumn> columns,
            Instant expiresAt,
            UUID createdBy,
            Instant createdAt) {
        this.id = id;
        this.objectKey = objectKey;
        this.fileName = fileName;
        this.fileType = fileType;
        this.sheetName = sheetName;
        this.rowCount = rowCount;
        this.columns = columns == null ? List.of() : List.copyOf(columns);
        this.status = Status.UPLOADED;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getObjectKey() { return objectKey; }
    public String getFileName() { return fileName; }
    public ImportFileType getFileType() { return fileType; }
    public String getSheetName() { return sheetName; }
    public int getRowCount() { return rowCount; }
    public List<ImportColumn> getColumns() { return columns; }
    public Status getStatus() { return status; }
    public UUID getImportId() { return importId; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCommittedAt() { return committedAt; }

    public void markCommitting() {
        this.status = Status.COMMITTING;
    }

    public void markCommitted(UUID importId, Instant committedAt) {
        this.status = Status.COMMITTED;
        this.importId = importId;
        this.committedAt = committedAt;
    }
}
