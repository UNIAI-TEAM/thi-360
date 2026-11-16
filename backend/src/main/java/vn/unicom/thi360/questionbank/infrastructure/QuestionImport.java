package vn.unicom.thi360.questionbank.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lịch sử một lần nhập (spec 4.1, kiểu {@code ImportHistoryEntry} của spec 4.5): các số đếm
 * và danh sách dòng lỗi ghi cùng transaction với câu đã nhập, nhật ký và kết quả
 * idempotency.
 */
@Entity
@Table(name = "question_import", schema = "questionbank")
public class QuestionImport {

    @Id
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "single_count", nullable = false)
    private int singleCount;

    @Column(name = "set_count", nullable = false)
    private int setCount;

    @Column(name = "set_child_count", nullable = false)
    private int setChildCount;

    @Column(name = "skipped_duplicate_count", nullable = false)
    private int skippedDuplicateCount;

    @Column(name = "new_version_count", nullable = false)
    private int newVersionCount;

    @Column(name = "error_row_count", nullable = false)
    private int errorRowCount;

    @Column(name = "missing_knowledge_count", nullable = false)
    private int missingKnowledgeCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<ImportErrorLine> errors = List.of();

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "imported_by")
    private UUID importedBy;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    protected QuestionImport() {}

    public QuestionImport(
            UUID id,
            String fileName,
            int createdCount,
            int singleCount,
            int setCount,
            int setChildCount,
            int skippedDuplicateCount,
            int newVersionCount,
            int errorRowCount,
            int missingKnowledgeCount,
            List<ImportErrorLine> errors,
            long durationMs,
            UUID importedBy,
            Instant importedAt) {
        this.id = id;
        this.fileName = fileName;
        this.createdCount = createdCount;
        this.singleCount = singleCount;
        this.setCount = setCount;
        this.setChildCount = setChildCount;
        this.skippedDuplicateCount = skippedDuplicateCount;
        this.newVersionCount = newVersionCount;
        this.errorRowCount = errorRowCount;
        this.missingKnowledgeCount = missingKnowledgeCount;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.durationMs = durationMs;
        this.importedBy = importedBy;
        this.importedAt = importedAt;
    }

    public UUID getId() { return id; }
    public String getFileName() { return fileName; }
    public int getCreatedCount() { return createdCount; }
    public int getSingleCount() { return singleCount; }
    public int getSetCount() { return setCount; }
    public int getSetChildCount() { return setChildCount; }
    public int getSkippedDuplicateCount() { return skippedDuplicateCount; }
    public int getNewVersionCount() { return newVersionCount; }
    public int getErrorRowCount() { return errorRowCount; }
    public int getMissingKnowledgeCount() { return missingKnowledgeCount; }
    public List<ImportErrorLine> getErrors() { return errors; }
    public long getDurationMs() { return durationMs; }
    public UUID getImportedBy() { return importedBy; }
    public Instant getImportedAt() { return importedAt; }
}
