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
 * Nút kiến thức (MAPID) gắn với câu (spec 4.1): chỉ nút cấp Kiến thức; đúng một dòng
 * {@code is_primary} khi có ít nhất một dòng (chỉ mục duy nhất có điều kiện ở migration).
 * Câu con dùng bảng này để ghi đè MAPID của bộ (spec 9.2).
 */
@Entity
@Table(name = "question_knowledge", schema = "questionbank")
@IdClass(QuestionKnowledge.PK.class)
public class QuestionKnowledge {

    /** Khóa chính (question_id, knowledge_item_id). */
    public static class PK implements Serializable {
        private UUID questionId;
        private UUID knowledgeItemId;

        public PK() {}

        public PK(UUID questionId, UUID knowledgeItemId) {
            this.questionId = questionId;
            this.knowledgeItemId = knowledgeItemId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PK other
                    && Objects.equals(questionId, other.questionId)
                    && Objects.equals(knowledgeItemId, other.knowledgeItemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questionId, knowledgeItemId);
        }
    }

    @Id
    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Id
    @Column(name = "knowledge_item_id", nullable = false)
    private UUID knowledgeItemId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected QuestionKnowledge() {
    }

    public QuestionKnowledge(UUID questionId, UUID knowledgeItemId, boolean primary) {
        this.questionId = Objects.requireNonNull(questionId);
        this.knowledgeItemId = Objects.requireNonNull(knowledgeItemId);
        this.primary = primary;
    }

    public UUID getQuestionId() { return questionId; }
    public UUID getKnowledgeItemId() { return knowledgeItemId; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
}
