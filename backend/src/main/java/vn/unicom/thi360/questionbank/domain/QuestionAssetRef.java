package vn.unicom.thi360.questionbank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Dòng liên kết tài nguyên ↔ phiên bản câu (spec 4.1 {@code question_asset_ref}), ghi trong
 * cùng transaction với lần lưu phiên bản. Tài nguyên không có dòng nào sau 24 giờ bị worker
 * dọn (plan D/BE-2b); tài nguyên của phiên bản cũ giữ dòng nên không bị xóa.
 */
@Entity
@Table(name = "question_asset_ref", schema = "questionbank")
@IdClass(QuestionAssetRef.PK.class)
public class QuestionAssetRef {

    /** Khóa chính (asset_id, question_id, version_no). */
    public static class PK implements Serializable {
        private UUID assetId;
        private UUID questionId;
        private Integer versionNo;

        public PK() {}

        public PK(UUID assetId, UUID questionId, Integer versionNo) {
            this.assetId = assetId;
            this.questionId = questionId;
            this.versionNo = versionNo;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PK other
                    && Objects.equals(assetId, other.assetId)
                    && Objects.equals(questionId, other.questionId)
                    && Objects.equals(versionNo, other.versionNo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(assetId, questionId, versionNo);
        }
    }

    @Id
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Id
    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Id
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    protected QuestionAssetRef() {
    }

    public QuestionAssetRef(UUID assetId, UUID questionId, Integer versionNo) {
        this.assetId = Objects.requireNonNull(assetId);
        this.questionId = Objects.requireNonNull(questionId);
        this.versionNo = Objects.requireNonNull(versionNo);
    }

    public UUID getAssetId() { return assetId; }
    public UUID getQuestionId() { return questionId; }
    public Integer getVersionNo() { return versionNo; }
}
