package vn.unicom.thi360.questionbank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Nhãn (giá trị nhóm QUESTION_TAG) gắn với câu (spec 4.1). */
@Entity
@Table(name = "question_tag", schema = "questionbank")
@IdClass(QuestionTag.PK.class)
public class QuestionTag {

    /** Khóa chính (question_id, tag_item_id). */
    public static class PK implements Serializable {
        private UUID questionId;
        private UUID tagItemId;

        public PK() {}

        public PK(UUID questionId, UUID tagItemId) {
            this.questionId = questionId;
            this.tagItemId = tagItemId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PK other
                    && Objects.equals(questionId, other.questionId)
                    && Objects.equals(tagItemId, other.tagItemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questionId, tagItemId);
        }
    }

    @Id
    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Id
    @Column(name = "tag_item_id", nullable = false)
    private UUID tagItemId;

    protected QuestionTag() {
    }

    public QuestionTag(UUID questionId, UUID tagItemId) {
        this.questionId = Objects.requireNonNull(questionId);
        this.tagItemId = Objects.requireNonNull(tagItemId);
    }

    public UUID getQuestionId() { return questionId; }
    public UUID getTagItemId() { return tagItemId; }
}
