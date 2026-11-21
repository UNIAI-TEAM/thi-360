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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Manual essay grade claim, draft, or finalized score for an {@link AttemptItem} (LT §4, §9.2, F17).
 * At most one active row ({@code superseded_by IS NULL}) exists per {@code attempt_item_id}.
 */
@Entity
@Table(name = "essay_grade", schema = "attempt")
public class EssayGrade {

    @Id
    private UUID id;

    @Column(name = "attempt_item_id", nullable = false, updatable = false)
    private UUID attemptItemId;

    @Column(name = "grader_id", nullable = false, updatable = false)
    private UUID graderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria", columnDefinition = "jsonb")
    private List<EssayCriterionScore> criteria;

    @Column(name = "points", precision = 10, scale = 4)
    private BigDecimal points;

    @Column(name = "comment")
    private String comment;

    @Column(name = "grading_guide_version", updatable = false)
    private Integer gradingGuideVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private EssayGradeState state;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "reopened_reason")
    private String reopenedReason;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EssayGrade() {}

    private EssayGrade(
            UUID id,
            UUID attemptItemId,
            UUID graderId,
            List<EssayCriterionScore> criteria,
            BigDecimal points,
            String comment,
            Integer gradingGuideVersion,
            Instant now,
            String reopenedReason) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.attemptItemId = Objects.requireNonNull(attemptItemId, "attemptItemId must not be null");
        this.graderId = Objects.requireNonNull(graderId, "graderId must not be null");
        this.criteria = criteria == null ? null : List.copyOf(criteria);
        this.points = points;
        this.comment = comment;
        this.gradingGuideVersion = gradingGuideVersion;
        this.state = EssayGradeState.DRAFT;
        this.claimedAt = Objects.requireNonNull(now, "now must not be null");
        this.lastActivityAt = now;
        this.reopenedReason = reopenedReason;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Creates an initial active {@code DRAFT} claim for an essay item. */
    public static EssayGrade claim(
            UUID id,
            UUID attemptItemId,
            UUID graderId,
            Integer gradingGuideVersion,
            Instant now) {
        return new EssayGrade(id, attemptItemId, graderId, null, null, null, gradingGuideVersion, now, null);
    }

    /** Creates an active {@code DRAFT} taking over an idle-expired draft, preserving any reopened reason. */
    public static EssayGrade takeover(
            UUID id,
            UUID attemptItemId,
            UUID graderId,
            Integer gradingGuideVersion,
            EssayGrade expired,
            Instant now) {
        Objects.requireNonNull(expired, "expired must not be null");
        return new EssayGrade(
                id,
                attemptItemId,
                graderId,
                null,
                null,
                null,
                gradingGuideVersion,
                now,
                expired.getReopenedReason());
    }

    /** Creates a reopened active {@code DRAFT} claim prefilled with the previous finalized scores. */
    public static EssayGrade reopenFrom(
            UUID id,
            UUID attemptItemId,
            UUID graderId,
            Integer gradingGuideVersion,
            EssayGrade previous,
            String reopenedReason,
            Instant now) {
        Objects.requireNonNull(previous, "previous must not be null");
        return new EssayGrade(
                id,
                attemptItemId,
                graderId,
                previous.getCriteria(),
                previous.getPoints(),
                previous.getComment(),
                gradingGuideVersion,
                now,
                reopenedReason);
    }

    /** Refreshes {@code lastActivityAt} when the current holder re-claims their own active draft. */
    public void touch(Instant now) {
        requireActiveDraft("touch");
        this.lastActivityAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /** Saves draft criteria, points, and comment while refreshing {@code lastActivityAt}. */
    public void saveDraft(
            List<EssayCriterionScore> criteria,
            BigDecimal points,
            String comment,
            Instant now) {
        requireActiveDraft("saveDraft");
        this.criteria = criteria == null ? null : List.copyOf(criteria);
        this.points = points;
        this.comment = comment;
        this.lastActivityAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /** Transitions an active draft to {@code FINAL} with validated scores. */
    public void finalizeGrade(
            List<EssayCriterionScore> criteria,
            BigDecimal points,
            String comment,
            Instant now) {
        requireActiveDraft("finalize");
        this.criteria = criteria == null ? null : List.copyOf(criteria);
        this.points = Objects.requireNonNull(points, "points must not be null");
        this.comment = comment;
        this.state = EssayGradeState.FINAL;
        this.lastActivityAt = Objects.requireNonNull(now, "now must not be null");
        this.finalizedAt = now;
        this.updatedAt = now;
    }

    /** Marks this grade row as superseded by a replacement claim or reopen draft. */
    public void markSuperseded(UUID replacementId, Instant now) {
        if (this.supersededBy != null) {
            throw new IllegalStateException("EssayGrade is already superseded");
        }
        this.supersededBy = Objects.requireNonNull(replacementId, "replacementId must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void requireActiveDraft(String op) {
        if (this.supersededBy != null) {
            throw new IllegalStateException("Cannot " + op + " on superseded EssayGrade");
        }
        if (this.state != EssayGradeState.DRAFT) {
            throw new IllegalStateException("Cannot " + op + " when state is " + this.state);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAttemptItemId() {
        return attemptItemId;
    }

    public UUID getGraderId() {
        return graderId;
    }

    public List<EssayCriterionScore> getCriteria() {
        return criteria;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public String getComment() {
        return comment;
    }

    public Integer getGradingGuideVersion() {
        return gradingGuideVersion;
    }

    public EssayGradeState getState() {
        return state;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public String getReopenedReason() {
        return reopenedReason;
    }

    public UUID getSupersededBy() {
        return supersededBy;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
