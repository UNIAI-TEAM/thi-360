package vn.unicom.thi360.questionbank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Bản ghi câu hỏi (spec 4.1). Câu con của bộ câu hỏi là dòng riêng có {@code parentId} +
 * {@code position}; phân loại câu con kế thừa của bộ khi cột là {@code null} (môn luôn kế
 * thừa — câu con không có {@code subjectItemId} riêng).
 *
 * <p>{@code revision} là khóa lạc quan (D13), tăng ở mọi lần ghi. {@code currentVersionNo} là
 * bản đang phục vụ (null khi chưa từng Sẵn sàng); {@code workingVersionNo} là bản Nháp đang
 * sửa (null khi không có). Câu con dùng {@code currentVersionNo} trỏ bản chụp của mình trong
 * {@code question_version} (version_no khớp của bộ), không có working riêng.
 */
@Entity
@Table(name = "question", schema = "questionbank")
public class Question {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "position")
    private Integer position;

    @Column(name = "subject_item_id")
    private UUID subjectItemId;

    @Column(name = "grade_item_id")
    private UUID gradeItemId;

    @Column(name = "level_item_id")
    private UUID levelItemId;

    @Column(name = "task_type_item_id")
    private UUID taskTypeItemId;

    @Column(name = "competency_item_id")
    private UUID competencyItemId;

    @Column(nullable = false)
    private BigDecimal points;

    @Column(name = "current_version_no")
    private Integer currentVersionNo;

    @Column(name = "working_version_no")
    private Integer workingVersionNo;

    @Column(name = "source_code")
    private String sourceCode;

    @Column(name = "source_code_normalized")
    private String sourceCodeNormalized;

    @Column(name = "content_text", nullable = false)
    private String contentText;

    @Column(name = "search_text", nullable = false)
    private String searchText;

    @Column(name = "has_solution", nullable = false)
    private boolean hasSolution;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long revision;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Question() {
    }

    public static Question create(UUID id, String code, QuestionType type, UUID parentId,
            Integer position, Instant now, UUID actorId) {
        Question q = new Question();
        q.id = Objects.requireNonNull(id);
        q.code = Objects.requireNonNull(code);
        q.type = Objects.requireNonNull(type);
        q.status = QuestionStatus.DRAFT;
        q.parentId = parentId;
        q.position = position;
        q.points = BigDecimal.ONE;
        q.contentText = "";
        q.searchText = "";
        q.hasSolution = false;
        q.createdAt = now;
        q.updatedAt = now;
        q.createdBy = actorId;
        q.updatedBy = actorId;
        return q;
    }

    /** Là câu con của một bộ câu hỏi. */
    public boolean isChild() {
        return parentId != null;
    }

    /** Câu độc lập (không phải câu con và chưa xóa mềm). */
    public boolean isStandalone() {
        return parentId == null && deletedAt == null;
    }

    public void touch(Instant now, UUID actorId) {
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    /** Phân loại có hiệu lực của câu con: riêng nếu đặt, kế thừa của bộ nếu để trống. */
    public UUID effectiveGradeItemId(Question parent) {
        return gradeItemId != null ? gradeItemId : (parent == null ? null : parent.getGradeItemId());
    }

    public UUID effectiveLevelItemId(Question parent) {
        return levelItemId != null ? levelItemId : (parent == null ? null : parent.getLevelItemId());
    }

    // ---------------------------------------------------------------- getters/setters

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public QuestionType getType() { return type; }
    public void setType(QuestionType type) { this.type = type; }
    public QuestionStatus getStatus() { return status; }
    public void setStatus(QuestionStatus status) { this.status = status; }
    public UUID getParentId() { return parentId; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public UUID getSubjectItemId() { return subjectItemId; }
    public void setSubjectItemId(UUID v) { this.subjectItemId = v; }
    public UUID getGradeItemId() { return gradeItemId; }
    public void setGradeItemId(UUID v) { this.gradeItemId = v; }
    public UUID getLevelItemId() { return levelItemId; }
    public void setLevelItemId(UUID v) { this.levelItemId = v; }
    public UUID getTaskTypeItemId() { return taskTypeItemId; }
    public void setTaskTypeItemId(UUID v) { this.taskTypeItemId = v; }
    public UUID getCompetencyItemId() { return competencyItemId; }
    public void setCompetencyItemId(UUID v) { this.competencyItemId = v; }
    public BigDecimal getPoints() { return points; }
    public void setPoints(BigDecimal points) { this.points = points; }
    public Integer getCurrentVersionNo() { return currentVersionNo; }
    public void setCurrentVersionNo(Integer v) { this.currentVersionNo = v; }
    public Integer getWorkingVersionNo() { return workingVersionNo; }
    public void setWorkingVersionNo(Integer v) { this.workingVersionNo = v; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String v) { this.sourceCode = v; }
    public String getSourceCodeNormalized() { return sourceCodeNormalized; }
    public void setSourceCodeNormalized(String v) { this.sourceCodeNormalized = v; }
    public String getContentText() { return contentText; }
    public void setContentText(String v) { this.contentText = v == null ? "" : v; }
    public String getSearchText() { return searchText; }
    public void setSearchText(String v) { this.searchText = v == null ? "" : v; }
    public boolean isHasSolution() { return hasSolution; }
    public void setHasSolution(boolean v) { this.hasSolution = v; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Long getRevision() { return revision; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant v) { this.deletedAt = v; }
}
