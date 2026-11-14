package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_row", schema = "identity")
public class ImportRow {

    @Id
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw", nullable = false, columnDefinition = "jsonb")
    private ImportRowRaw raw;

    @Column(name = "username_normalized")
    private String usernameNormalized;

    @Column(name = "email_normalized")
    private String emailNormalized;

    @Column(name = "class_code_normalized")
    private String classCodeNormalized;

    @Column(name = "resolved_class_id")
    private UUID resolvedClassId;

    @Column(name = "password_generated", nullable = false)
    private boolean passwordGenerated;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", nullable = false, columnDefinition = "jsonb")
    private List<ImportRowError> errors;

    @Column(name = "status", nullable = false)
    private ImportRowStatus status;

    @Column(name = "created_user_id")
    private UUID createdUserId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected ImportRow() {
    }

    public ImportRow(
            UUID id,
            UUID batchId,
            int rowNo,
            ImportRowRaw raw,
            String usernameNormalized,
            String emailNormalized,
            String classCodeNormalized,
            UUID resolvedClassId,
            boolean passwordGenerated,
            List<ImportRowError> errors) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.batchId = Objects.requireNonNull(batchId, "batchId must not be null");
        this.rowNo = rowNo;
        this.raw = Objects.requireNonNull(raw, "raw must not be null");
        this.usernameNormalized = usernameNormalized;
        this.emailNormalized = emailNormalized;
        this.classCodeNormalized = classCodeNormalized;
        this.resolvedClassId = resolvedClassId;
        this.passwordGenerated = passwordGenerated;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.status = this.errors.isEmpty() ? ImportRowStatus.VALID : ImportRowStatus.INVALID;
    }

    public void updateValidation(UUID resolvedClassId, List<ImportRowError> errors) {
        this.resolvedClassId = resolvedClassId;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.status = this.errors.isEmpty() ? ImportRowStatus.VALID : ImportRowStatus.INVALID;
    }

    public void markCreated(UUID createdUserId, Instant processedAt) {
        this.status = ImportRowStatus.CREATED;
        this.createdUserId = Objects.requireNonNull(createdUserId, "createdUserId must not be null");
        this.failureCode = null;
        this.failureReason = null;
        this.processedAt = processedAt;
        this.raw = this.raw.withoutPassword();
    }

    public void markFailed(String failureCode, String failureReason, boolean wipePassword, Instant processedAt) {
        this.status = ImportRowStatus.FAILED;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.processedAt = processedAt;
        if (wipePassword) {
            this.raw = this.raw.withoutPassword();
        }
    }

    public void wipePassword() {
        if (this.raw != null && this.raw.passwordEnc() != null) {
            this.raw = this.raw.withoutPassword();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public int getRowNo() {
        return rowNo;
    }

    public ImportRowRaw getRaw() {
        return raw;
    }

    public String getUsernameNormalized() {
        return usernameNormalized;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getClassCodeNormalized() {
        return classCodeNormalized;
    }

    public UUID getResolvedClassId() {
        return resolvedClassId;
    }

    public boolean isPasswordGenerated() {
        return passwordGenerated;
    }

    public List<ImportRowError> getErrors() {
        return errors == null ? List.of() : errors;
    }

    public ImportRowStatus getStatus() {
        return status;
    }

    public UUID getCreatedUserId() {
        return createdUserId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
