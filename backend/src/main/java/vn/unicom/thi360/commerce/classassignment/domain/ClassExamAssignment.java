package vn.unicom.thi360.commerce.classassignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "class_exam_assignment", schema = "commerce")
public class ClassExamAssignment {

    @Id
    private UUID id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private UUID classId;

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(name = "exam_version_no", nullable = false, updatable = false)
    private int examVersionNo;

    @Column(name = "exam_code", nullable = false, updatable = false)
    private String examCode;

    @Column(name = "exam_name", nullable = false, updatable = false)
    private String examName;

    @Column(name = "attempts_granted", nullable = false, updatable = false)
    private int attemptsGranted;

    @Column(name = "result_view", nullable = false, updatable = false)
    private String resultView;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "option_codes", nullable = false, updatable = false, columnDefinition = "text[]")
    private String[] optionCodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> benefits;

    @Column(name = "auto_grant_new_members", nullable = false)
    private boolean autoGrantNewMembers;

    @Column(name = "note", updatable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClassExamAssignmentStatus status;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason")
    private String revokeReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ClassExamAssignment() {}

    public ClassExamAssignment(
            UUID id,
            UUID classId,
            UUID examId,
            int examVersionNo,
            String examCode,
            String examName,
            int attemptsGranted,
            String resultView,
            List<String> optionCodes,
            Map<String, Object> benefits,
            boolean autoGrantNewMembers,
            String note,
            UUID assignedBy,
            Instant assignedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classId = Objects.requireNonNull(classId, "classId must not be null");
        this.examId = Objects.requireNonNull(examId, "examId must not be null");
        this.examVersionNo = examVersionNo;
        this.examCode = Objects.requireNonNull(examCode, "examCode must not be null");
        this.examName = Objects.requireNonNull(examName, "examName must not be null");
        this.attemptsGranted = attemptsGranted;
        this.resultView = Objects.requireNonNull(resultView, "resultView must not be null");
        this.optionCodes = optionCodes == null ? new String[0] : optionCodes.toArray(String[]::new);
        this.benefits = copyNonNull(benefits);
        this.autoGrantNewMembers = autoGrantNewMembers;
        this.note = note;
        this.status = ClassExamAssignmentStatus.ACTIVE;
        this.assignedBy = Objects.requireNonNull(assignedBy, "assignedBy must not be null");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt must not be null");
        this.updatedAt = assignedAt;
    }

    public void updateAutoGrantNewMembers(boolean autoGrantNewMembers, Instant now) {
        this.autoGrantNewMembers = autoGrantNewMembers;
        this.updatedAt = Objects.requireNonNull(now);
    }

    public boolean markRevoked(UUID actorId, String reason, Instant now) {
        if (this.status == ClassExamAssignmentStatus.REVOKED) {
            return false;
        }
        this.status = ClassExamAssignmentStatus.REVOKED;
        this.autoGrantNewMembers = false;
        this.revokedBy = actorId;
        this.revokedAt = Objects.requireNonNull(now);
        this.revokeReason = reason;
        this.updatedAt = now;
        return true;
    }

    private static Map<String, Object> copyNonNull(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((k, v) -> {
            if (k != null && v != null) {
                copy.put(k, v);
            }
        });
        return Map.copyOf(copy);
    }

    public UUID getId() { return id; }
    public UUID getClassId() { return classId; }
    public UUID getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public String getExamCode() { return examCode; }
    public String getExamName() { return examName; }
    public int getAttemptsGranted() { return attemptsGranted; }
    public String getResultView() { return resultView; }
    public List<String> getOptionCodes() { return List.of(optionCodes); }
    public Map<String, Object> getBenefits() { return benefits; }
    public boolean isAutoGrantNewMembers() { return autoGrantNewMembers; }
    public String getNote() { return note; }
    public ClassExamAssignmentStatus getStatus() { return status; }
    public UUID getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public UUID getRevokedBy() { return revokedBy; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
