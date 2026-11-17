package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "exam_structure", schema = "exam")
public class ExamStructure {
    @Id
    @Column(name = "exam_id", nullable = false)
    private UUID examId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parts", nullable = false, columnDefinition = "jsonb")
    private JsonNode parts;
    @Column(name = "structure_hash")
    private String structureHash;
    @Column(name = "base_version")
    private Integer baseVersion;
    @Column(name = "edited", nullable = false)
    private boolean edited;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExamStructure() {
    }

    public ExamStructure(UUID examId, JsonNode parts, String structureHash, Integer baseVersion,
            boolean edited, UUID updatedBy, Instant updatedAt) {
        this.examId = examId;
        this.parts = parts;
        this.structureHash = structureHash;
        this.baseVersion = baseVersion;
        this.edited = edited;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public UUID getExamId() { return examId; }
    public JsonNode getParts() { return parts; }
    public String getStructureHash() { return structureHash; }
    public Integer getBaseVersion() { return baseVersion; }
    public UUID getUpdatedBy() { return updatedBy; }
    public boolean isEdited() { return edited; }

    /** Thay toàn bộ tài liệu cấu trúc; {@code edited} đánh dấu đã sửa kể từ lần nạp mẫu gần nhất (spec 5.2). */
    public void replace(JsonNode newParts, String newHash, boolean edited, UUID actorId, Instant now) {
        this.parts = newParts;
        this.structureHash = newHash;
        this.edited = edited;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }
}
