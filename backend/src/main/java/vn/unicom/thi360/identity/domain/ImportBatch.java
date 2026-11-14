package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "import_batch", schema = "identity")
public class ImportBatch {

    @Id
    private UUID id;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "file_object_key")
    private String fileObjectKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "status", nullable = false)
    private ImportBatchStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(name = "created_rows", nullable = false)
    private int createdRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "result_object_key")
    private String resultObjectKey;

    @Column(name = "result_expires_at")
    private Instant resultExpiresAt;

    @Column(name = "result_deleted_at")
    private Instant resultDeletedAt;

    @Column(name = "source_deleted_at")
    private Instant sourceDeletedAt;

    @Column(name = "has_generated_passwords", nullable = false)
    private boolean hasGeneratedPasswords;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_by_username")
    private String createdByUsername;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ImportBatch() {
    }

    public ImportBatch(
            UUID id,
            String fileObjectKey,
            String originalFilename,
            boolean mustChangePassword,
            boolean hasGeneratedPasswords,
            UUID createdBy,
            String createdByUsername,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.fileObjectKey = fileObjectKey;
        this.originalFilename = originalFilename;
        this.mustChangePassword = mustChangePassword;
        this.hasGeneratedPasswords = hasGeneratedPasswords;
        this.status = ImportBatchStatus.UPLOADED;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdByUsername = createdByUsername;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        this.updatedBy = createdBy;
    }

    public void markValidated(int totalRows, int validRows, int invalidRows, Instant now) {
        this.totalRows = totalRows;
        this.validRows = validRows;
        this.invalidRows = invalidRows;
        this.status = ImportBatchStatus.VALIDATED;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void updateValidationCounts(int validRows, int invalidRows, Instant now) {
        this.validRows = validRows;
        this.invalidRows = invalidRows;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markConfirmed(String idempotencyKey, UUID actorId, Instant now) {
        this.idempotencyKey = idempotencyKey;
        this.status = ImportBatchStatus.CONFIRMED;
        this.confirmedAt = now;
        this.confirmedBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void markProcessing(Instant now) {
        this.status = ImportBatchStatus.PROCESSING;
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        this.updatedAt = now;
    }

    public void markFinished(String resultObjectKey, int createdRows, int failedRows, Instant now) {
        this.resultObjectKey = resultObjectKey;
        this.createdRows = createdRows;
        this.failedRows = failedRows;
        this.status = failedRows == 0 ? ImportBatchStatus.COMPLETED : ImportBatchStatus.COMPLETED_WITH_ERRORS;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void markFailed(String errorMessage, int createdRows, int failedRows, Instant now) {
        this.status = ImportBatchStatus.FAILED;
        this.errorMessage = errorMessage;
        this.createdRows = createdRows;
        this.failedRows = failedRows;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void markSourceDeleted(Instant now) {
        this.fileObjectKey = null;
        this.sourceDeletedAt = now;
        this.updatedAt = now;
    }

    public void markResultDeleted(UUID actorId, Instant now) {
        this.resultObjectKey = null;
        this.resultDeletedAt = now;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFileObjectKey() {
        return fileObjectKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public ImportBatchStatus getStatus() {
        return status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public int getInvalidRows() {
        return invalidRows;
    }

    public int getCreatedRows() {
        return createdRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public String getResultObjectKey() {
        return resultObjectKey;
    }

    public Instant getResultExpiresAt() {
        return resultExpiresAt;
    }

    public Instant getResultDeletedAt() {
        return resultDeletedAt;
    }

    public Instant getSourceDeletedAt() {
        return sourceDeletedAt;
    }

    public boolean isHasGeneratedPasswords() {
        return hasGeneratedPasswords;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public UUID getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Long getVersion() {
        return version;
    }
}
