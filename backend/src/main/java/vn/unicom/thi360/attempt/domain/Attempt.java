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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Root entity for an attempt on a published exam version (LT §4, BS §4). */
@Entity
@Table(name = "attempt", schema = "attempt")
public class Attempt {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(name = "exam_version_no", nullable = false, updatable = false)
    private int examVersionNo;

    @Column(name = "entitlement_id", nullable = false, updatable = false)
    private UUID entitlementId;

    @Column(name = "grant_id", nullable = false, updatable = false)
    private UUID grantId;

    @Column(name = "attempt_no")
    private Integer attemptNo;

    @Column(name = "exam_name", nullable = false, updatable = false)
    private String examName;

    @Column(name = "exam_kind_item_id", updatable = false)
    private UUID examKindItemId;

    @Column(name = "exam_kind_code", nullable = false, updatable = false)
    private String examKindCode;

    @Column(name = "scope", nullable = false, updatable = false)
    private String scope;

    @Column(name = "part_code", updatable = false)
    private String partCode;

    @Column(name = "subject_item_id", updatable = false)
    private UUID subjectItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "timing", nullable = false, updatable = false)
    private AttemptTiming timing;

    @Enumerated(EnumType.STRING)
    @Column(name = "navigation_mode", nullable = false, updatable = false)
    private NavigationMode navigationMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> choices;

    @Column(name = "seed", nullable = false, updatable = false)
    private long seed;

    @Column(name = "generation_token", nullable = false)
    private UUID generationToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private AttemptState state;

    @Column(name = "regrading", nullable = false)
    private boolean regrading;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason")
    private EndReason endReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "abort_reason")
    private AbortReason abortReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "edit_session_id")
    private UUID editSessionId;

    @Column(name = "edit_session_seen_at")
    private Instant editSessionSeenAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_position", columnDefinition = "jsonb")
    private Map<String, Object> lastPosition;

    @Column(name = "result_view", nullable = false, updatable = false)
    private String resultView;

    @Column(name = "practice_scoring", updatable = false)
    private String practiceScoring;

    @Column(name = "reference_max", precision = 10, scale = 4)
    private BigDecimal referenceMax;

    @Column(name = "max_score", precision = 10, scale = 4)
    private BigDecimal maxScore;

    @Column(name = "raw_score", precision = 10, scale = 4)
    private BigDecimal rawScore;

    @Column(name = "display_score", precision = 10, scale = 4)
    private BigDecimal displayScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @Column(name = "grading_error")
    private String gradingError;

    @Column(name = "fault_flag")
    private String faultFlag;

    @Column(name = "fault_reason")
    private String faultReason;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Attempt() {
    }

    private Attempt(
            UUID id,
            UUID userId,
            UUID examId,
            int examVersionNo,
            UUID entitlementId,
            UUID grantId,
            String examName,
            UUID examKindItemId,
            String examKindCode,
            String scope,
            String partCode,
            UUID subjectItemId,
            AttemptTiming timing,
            NavigationMode navigationMode,
            Map<String, Object> choices,
            long seed,
            UUID generationToken,
            String resultView,
            String practiceScoring,
            BigDecimal referenceMax,
            BigDecimal maxScore,
            String idempotencyKey,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.examId = Objects.requireNonNull(examId, "examId must not be null");
        this.examVersionNo = examVersionNo;
        this.entitlementId = Objects.requireNonNull(entitlementId, "entitlementId must not be null");
        this.grantId = Objects.requireNonNull(grantId, "grantId must not be null");
        this.examName = Objects.requireNonNull(examName, "examName must not be null");
        this.examKindItemId = examKindItemId;
        this.examKindCode = Objects.requireNonNull(examKindCode, "examKindCode must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.partCode = partCode;
        this.subjectItemId = subjectItemId;
        this.timing = Objects.requireNonNull(timing, "timing must not be null");
        this.navigationMode = Objects.requireNonNull(navigationMode, "navigationMode must not be null");
        this.choices = choices == null ? Map.of() : Map.copyOf(choices);
        this.seed = seed;
        this.generationToken = Objects.requireNonNull(generationToken, "generationToken must not be null");
        this.state = AttemptState.STARTING;
        this.regrading = false;
        this.resultView = Objects.requireNonNull(resultView, "resultView must not be null");
        this.practiceScoring = practiceScoring;
        this.referenceMax = referenceMax;
        this.maxScore = maxScore;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static Attempt start(
            UUID id,
            UUID userId,
            UUID examId,
            int examVersionNo,
            UUID entitlementId,
            UUID grantId,
            String examName,
            UUID examKindItemId,
            String examKindCode,
            String scope,
            String partCode,
            UUID subjectItemId,
            AttemptTiming timing,
            NavigationMode navigationMode,
            Map<String, Object> choices,
            long seed,
            UUID generationToken,
            String resultView,
            String practiceScoring,
            BigDecimal referenceMax,
            BigDecimal maxScore,
            String idempotencyKey,
            Instant now) {
        return new Attempt(
                id, userId, examId, examVersionNo, entitlementId, grantId,
                examName, examKindItemId, examKindCode, scope, partCode, subjectItemId,
                timing, navigationMode, choices, seed, generationToken,
                resultView, practiceScoring, referenceMax, maxScore, idempotencyKey, now);
    }

    /** Opens the attempt after background paper generation succeeds (LT §5.2 step 3, F13). */
    public void open(int attemptNo, Instant startedAt, Instant deadlineAt) {
        if (this.state != AttemptState.STARTING) {
            throw new IllegalStateException("Cannot open attempt from state " + this.state);
        }
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be positive");
        }
        this.attemptNo = attemptNo;
        this.state = AttemptState.IN_PROGRESS;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.deadlineAt = deadlineAt;
        this.updatedAt = startedAt;
    }

    public void updateMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    /** Aborts a STARTING attempt without assigning attemptNo (LT §5.2 step 4, AC-10). */
    public void abort(AbortReason reason, Instant now) {
        if (this.state != AttemptState.STARTING) {
            throw new IllegalStateException("Cannot abort attempt from state " + this.state);
        }
        this.state = AttemptState.ABORTED;
        this.abortReason = Objects.requireNonNull(reason, "reason must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /** Closes an IN_PROGRESS attempt into GRADING using the shared close procedure (LT §6.3). */
    public void close(EndReason endReason, Instant finishedAt, Instant now) {
        if (this.state != AttemptState.IN_PROGRESS) {
            throw new IllegalStateException("Cannot close attempt from state " + this.state);
        }
        this.state = AttemptState.GRADING;
        this.endReason = Objects.requireNonNull(endReason, "endReason must not be null");
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * Applies aggregated grading output and transitions to {@code SCORED} or {@code PENDING_REVIEW}.
     * When {@code regrading == true} and essays remain pending, preserves prior scored totals per LT §9.2.
     */
    public void applyGradingResult(
            boolean pendingReview,
            BigDecimal maxScore,
            BigDecimal rawScore,
            BigDecimal displayScore,
            Map<String, Object> result,
            Instant gradedAt,
            Instant now) {
        if (this.state != AttemptState.GRADING
                && this.state != AttemptState.GRADING_ERROR
                && this.state != AttemptState.PENDING_REVIEW
                && this.state != AttemptState.SCORED) {
            throw new IllegalStateException("Cannot apply grading result from state " + this.state);
        }
        this.maxScore = maxScore;
        this.result = result;
        this.gradingError = null;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        if (pendingReview) {
            this.state = AttemptState.PENDING_REVIEW;
            if (!this.regrading) {
                this.rawScore = null;
                this.displayScore = null;
            }
        } else {
            this.state = AttemptState.SCORED;
            this.regrading = false;
            this.rawScore = rawScore;
            this.displayScore = displayScore;
            this.gradedAt = Objects.requireNonNull(gradedAt, "gradedAt must not be null");
        }
    }

    /**
     * Marks grading failure after outbox retries are exhausted; never sets score to zero (AC-20).
     */
    public void markGradingError(String errorMessage, Instant now) {
        if (this.state != AttemptState.GRADING && this.state != AttemptState.GRADING_ERROR) {
            throw new IllegalStateException("Cannot mark grading error from state " + this.state);
        }
        String clean = (errorMessage == null || errorMessage.isBlank()) ? "GRADING_FAILED" : errorMessage.trim();
        this.state = AttemptState.GRADING_ERROR;
        this.gradingError = clean.length() > 1000 ? clean.substring(0, 1000) : clean;
        if (!this.regrading) {
            this.rawScore = null;
            this.displayScore = null;
        }
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /** Claims the initial active edit session for an in-progress attempt (LT F10). */
    public void claimSession(UUID sessionId, Instant seenAt) {
        if (this.state != AttemptState.IN_PROGRESS) {
            throw new IllegalStateException("Cannot claim session from state " + this.state);
        }
        this.editSessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.editSessionSeenAt = Objects.requireNonNull(seenAt, "seenAt must not be null");
        this.updatedAt = seenAt;
    }

    /** Replaces the active edit session when another device confirms takeover (LT F10, AC-11.07). */
    public void takeoverSession(UUID sessionId, Instant seenAt) {
        if (this.state != AttemptState.IN_PROGRESS) {
            throw new IllegalStateException("Cannot take over session from state " + this.state);
        }
        this.editSessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.editSessionSeenAt = Objects.requireNonNull(seenAt, "seenAt must not be null");
        this.updatedAt = seenAt;
    }

    /** Refreshes the last-seen timestamp of the current edit session (LT §7.1). */
    public void touchSession(Instant seenAt) {
        if (this.state != AttemptState.IN_PROGRESS) {
            throw new IllegalStateException("Cannot touch session from state " + this.state);
        }
        this.editSessionSeenAt = Objects.requireNonNull(seenAt, "seenAt must not be null");
        this.updatedAt = seenAt;
    }

    /** Updates the student's last viewed part/item position on an in-progress attempt (LT §7.1). */
    public void updateLastPosition(Map<String, Object> lastPosition, Instant now) {
        if (this.state != AttemptState.IN_PROGRESS) {
            throw new IllegalStateException("Cannot update position from state " + this.state);
        }
        this.lastPosition =
                lastPosition == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(lastPosition));
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getExamId() {
        return examId;
    }

    public int getExamVersionNo() {
        return examVersionNo;
    }

    public UUID getEntitlementId() {
        return entitlementId;
    }

    public UUID getGrantId() {
        return grantId;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public String getExamName() {
        return examName;
    }

    public UUID getExamKindItemId() {
        return examKindItemId;
    }

    public String getExamKindCode() {
        return examKindCode;
    }

    public String getScope() {
        return scope;
    }

    public String getPartCode() {
        return partCode;
    }

    public UUID getSubjectItemId() {
        return subjectItemId;
    }

    public AttemptTiming getTiming() {
        return timing;
    }

    public NavigationMode getNavigationMode() {
        return navigationMode;
    }

    public Map<String, Object> getChoices() {
        return choices;
    }

    public long getSeed() {
        return seed;
    }

    public UUID getGenerationToken() {
        return generationToken;
    }

    public AttemptState getState() {
        return state;
    }

    public boolean isRegrading() {
        return regrading;
    }

    public EndReason getEndReason() {
        return endReason;
    }

    public AbortReason getAbortReason() {
        return abortReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public UUID getEditSessionId() {
        return editSessionId;
    }

    public Instant getEditSessionSeenAt() {
        return editSessionSeenAt;
    }

    public Map<String, Object> getLastPosition() {
        return lastPosition;
    }

    public String getResultView() {
        return resultView;
    }

    public String getPracticeScoring() {
        return practiceScoring;
    }

    public BigDecimal getReferenceMax() {
        return referenceMax;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public BigDecimal getRawScore() {
        return rawScore;
    }

    public BigDecimal getDisplayScore() {
        return displayScore;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public Instant getGradedAt() {
        return gradedAt;
    }

    public String getGradingError() {
        return gradingError;
    }

    public String getFaultFlag() {
        return faultFlag;
    }

    public String getFaultReason() {
        return faultReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
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
