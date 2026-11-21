package vn.unicom.thi360.attempt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Question item issued to a part of an {@link Attempt} (LT §4, BS §5, BS §17.5). */
@Entity
@Table(name = "attempt_item", schema = "attempt")
public class AttemptItem {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "part_id", nullable = false, updatable = false)
    private UUID partId;

    @Column(name = "subject_item_id", updatable = false)
    private UUID subjectItemId;

    @Column(name = "section_id", nullable = false, updatable = false)
    private String sectionId;

    @Column(name = "component_code", updatable = false)
    private String componentCode;

    @Column(name = "block_id", nullable = false, updatable = false)
    private String blockId;

    @Column(name = "parent_item_id", updatable = false)
    private UUID parentItemId;

    @Column(name = "ord", nullable = false, updatable = false)
    private int ord;

    @Column(name = "display_no", updatable = false)
    private Integer displayNo;

    @Column(name = "unit_count", nullable = false, updatable = false)
    private int unitCount;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "question_version_no", nullable = false, updatable = false)
    private int questionVersionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, updatable = false)
    private QuestionType questionType;

    @Column(name = "points_max", precision = 10, scale = 4, nullable = false, updatable = false)
    private BigDecimal pointsMax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_order", columnDefinition = "jsonb")
    private List<String> optionOrder;

    @Column(name = "in_scope", nullable = false)
    private boolean inScope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", columnDefinition = "jsonb")
    private AnswerValue answer;

    @Column(name = "answer_seq", nullable = false)
    private long answerSeq;

    @Column(name = "answer_received_at")
    private Instant answerReceivedAt;

    @Column(name = "flagged", nullable = false)
    private boolean flagged;

    @Column(name = "earned_points", precision = 10, scale = 4)
    private BigDecimal earnedPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome")
    private ItemOutcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grading_detail", columnDefinition = "jsonb")
    private Map<String, Object> gradingDetail;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AttemptItem() {
    }

    private AttemptItem(
            UUID id,
            UUID attemptId,
            UUID partId,
            UUID subjectItemId,
            String sectionId,
            String componentCode,
            String blockId,
            UUID parentItemId,
            int ord,
            Integer displayNo,
            int unitCount,
            UUID questionId,
            int questionVersionNo,
            QuestionType questionType,
            BigDecimal pointsMax,
            List<String> optionOrder,
            boolean inScope) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId must not be null");
        this.partId = Objects.requireNonNull(partId, "partId must not be null");
        this.subjectItemId = subjectItemId;
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId must not be null");
        this.componentCode = componentCode;
        this.blockId = Objects.requireNonNull(blockId, "blockId must not be null");
        this.parentItemId = parentItemId;
        this.ord = ord;
        this.displayNo = displayNo;
        this.unitCount = unitCount;
        this.questionId = Objects.requireNonNull(questionId, "questionId must not be null");
        this.questionVersionNo = questionVersionNo;
        this.questionType = Objects.requireNonNull(questionType, "questionType must not be null");
        this.pointsMax = Objects.requireNonNull(pointsMax, "pointsMax must not be null");
        this.optionOrder = optionOrder == null ? null : List.copyOf(optionOrder);
        this.inScope = inScope;
        this.answerSeq = 0L;
        this.flagged = false;
    }

    public static AttemptItem create(
            UUID id,
            UUID attemptId,
            UUID partId,
            UUID subjectItemId,
            String sectionId,
            String componentCode,
            String blockId,
            UUID parentItemId,
            int ord,
            Integer displayNo,
            int unitCount,
            UUID questionId,
            int questionVersionNo,
            QuestionType questionType,
            BigDecimal pointsMax,
            List<String> optionOrder) {
        return new AttemptItem(
                id, attemptId, partId, subjectItemId, sectionId, componentCode, blockId, parentItemId,
                ord, displayNo, unitCount, questionId, questionVersionNo, questionType, pointsMax, optionOrder, true);
    }

    public void recordAnswer(AnswerValue answer, long seq, Instant receivedAt) {
        if (seq <= this.answerSeq) {
            throw new IllegalArgumentException("Answer seq must increase: " + seq + " <= " + this.answerSeq);
        }
        this.answer = answer;
        this.answerSeq = seq;
        this.answerReceivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public void recordGrading(BigDecimal earnedPoints, ItemOutcome outcome, Map<String, Object> gradingDetail) {
        this.earnedPoints = earnedPoints;
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.gradingDetail = gradingDetail;
    }

    /** Returns true when this essay item has already received a finalized manual score. */
    public boolean isFinalizedManualEssay() {
        return this.questionType == QuestionType.ESSAY
                && this.outcome != null
                && this.outcome != ItemOutcome.BLANK
                && this.outcome != ItemOutcome.PENDING_REVIEW
                && this.earnedPoints != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public UUID getPartId() {
        return partId;
    }

    public UUID getSubjectItemId() {
        return subjectItemId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getComponentCode() {
        return componentCode;
    }

    public String getBlockId() {
        return blockId;
    }

    public UUID getParentItemId() {
        return parentItemId;
    }

    public int getOrd() {
        return ord;
    }

    public Integer getDisplayNo() {
        return displayNo;
    }

    public int getUnitCount() {
        return unitCount;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public int getQuestionVersionNo() {
        return questionVersionNo;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public BigDecimal getPointsMax() {
        return pointsMax;
    }

    public List<String> getOptionOrder() {
        return optionOrder;
    }

    public boolean isInScope() {
        return inScope;
    }

    public AnswerValue getAnswer() {
        return answer;
    }

    public long getAnswerSeq() {
        return answerSeq;
    }

    public Instant getAnswerReceivedAt() {
        return answerReceivedAt;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public BigDecimal getEarnedPoints() {
        return earnedPoints;
    }

    public ItemOutcome getOutcome() {
        return outcome;
    }

    public Map<String, Object> getGradingDetail() {
        return gradingDetail;
    }

    public Long getVersion() {
        return version;
    }
}
