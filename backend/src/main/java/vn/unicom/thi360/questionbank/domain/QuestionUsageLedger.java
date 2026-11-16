package vn.unicom.thi360.questionbank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.unicom.thi360.questionbank.spi.QuestionUsageKind;

/**
 * Sổ "câu đã dùng" (spec 4.1, D18): một dòng cho mỗi bộ (câu, phiên bản, loại, bài).
 * BE-5b/BE-6 ghi ({@code PREVIEW}, {@code PUBLISH_WITNESS}, sau này {@code ATTEMPT});
 * lớp ứng dụng chỉ đọc để chặn xóa (409 {@code QUESTION_IN_USE}) và hiện nơi đang dùng.
 */
@Entity
@Table(name = "question_usage_ledger", schema = "questionbank")
@IdClass(QuestionUsageLedger.PK.class)
public class QuestionUsageLedger {

    /** Khóa chính (question_id, version_no, kind, exam_id). */
    public static class PK implements Serializable {
        private UUID questionId;
        private Integer versionNo;
        private QuestionUsageKind kind;
        private UUID examId;

        public PK() {}

        @Override
        public boolean equals(Object o) {
            return o instanceof PK other
                    && Objects.equals(questionId, other.questionId)
                    && Objects.equals(versionNo, other.versionNo)
                    && kind == other.kind
                    && Objects.equals(examId, other.examId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questionId, versionNo, kind, examId);
        }
    }

    @Id
    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Id
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionUsageKind kind;

    @Id
    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "first_at", nullable = false)
    private Instant firstAt;

    protected QuestionUsageLedger() {
    }

    public UUID getQuestionId() { return questionId; }
    public Integer getVersionNo() { return versionNo; }
    public QuestionUsageKind getKind() { return kind; }
    public UUID getExamId() { return examId; }
    public Instant getFirstAt() { return firstAt; }
}
