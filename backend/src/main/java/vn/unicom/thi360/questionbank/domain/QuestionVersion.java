package vn.unicom.thi360.questionbank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một phiên bản nội dung của câu (spec 4.1). {@code snapshot} là {@link QuestionSnapshot}.
 * Trạng thái phiên bản (WORKING / CURRENT / SUPERSEDED / RETIRED) suy ra từ hai con trỏ và
 * trạng thái trên {@link Question}, không lưu riêng.
 */
@Entity
@Table(name = "question_version", schema = "questionbank")
@IdClass(QuestionVersion.PK.class)
public class QuestionVersion {

    /** Khóa chính (question_id, version_no). */
    public static class PK implements Serializable {
        private UUID questionId;
        private Integer versionNo;

        public PK() {}

        public PK(UUID questionId, Integer versionNo) {
            this.questionId = questionId;
            this.versionNo = versionNo;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PK other
                    && Objects.equals(questionId, other.questionId)
                    && Objects.equals(versionNo, other.versionNo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questionId, versionNo);
        }
    }

    @Id
    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Id
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private QuestionSnapshot snapshot;

    @Column(name = "saved_by")
    private UUID savedBy;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    protected QuestionVersion() {
    }

    public QuestionVersion(UUID questionId, Integer versionNo, QuestionSnapshot snapshot,
            UUID savedBy, Instant savedAt) {
        this.questionId = Objects.requireNonNull(questionId);
        this.versionNo = Objects.requireNonNull(versionNo);
        this.snapshot = Objects.requireNonNull(snapshot);
        this.savedBy = savedBy;
        this.savedAt = Objects.requireNonNull(savedAt);
    }

    public UUID getQuestionId() { return questionId; }
    public Integer getVersionNo() { return versionNo; }
    public QuestionSnapshot getSnapshot() { return snapshot; }
    public void setSnapshot(QuestionSnapshot snapshot) { this.snapshot = snapshot; }
    public UUID getSavedBy() { return savedBy; }
    public Instant getSavedAt() { return savedAt; }
    public Instant getReadyAt() { return readyAt; }
    public void setReadyAt(Instant readyAt) { this.readyAt = readyAt; }
}
