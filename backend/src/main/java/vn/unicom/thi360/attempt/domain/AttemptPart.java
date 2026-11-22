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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Part belonging to an {@link Attempt} (LT §4). */
@Entity
@Table(name = "attempt_part", schema = "attempt")
public class AttemptPart {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "part_code", nullable = false, updatable = false)
    private String partCode;

    @Column(name = "part_name", nullable = false, updatable = false)
    private String partName;

    @Column(name = "ord", nullable = false, updatable = false)
    private int ord;

    @Column(name = "duration_minutes", updatable = false)
    private Integer durationMinutes;

    @Column(name = "question_count_required", updatable = false)
    private Integer questionCountRequired;

    @Column(name = "max_points", precision = 10, scale = 4, updatable = false)
    private BigDecimal maxPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private PartState state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choice", columnDefinition = "jsonb")
    private Map<String, Object> choice;

    @Column(name = "choice_confirmed_at")
    private Instant choiceConfirmedAt;

    @Column(name = "choice_deadline_at")
    private Instant choiceDeadlineAt;

    @Column(name = "seed", nullable = false, updatable = false)
    private long seed;

    @Column(name = "generating_since")
    private Instant generatingSince;

    @Column(name = "generation_token")
    private UUID generationToken;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason")
    private PartCloseReason closeReason;

    @Column(name = "generation_tries", nullable = false)
    private int generationTries;

    @Column(name = "generation_error")
    private String generationError;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AttemptPart() {
    }

    private AttemptPart(
            UUID id,
            UUID attemptId,
            String partCode,
            String partName,
            int ord,
            Integer durationMinutes,
            Integer questionCountRequired,
            BigDecimal maxPoints,
            PartState state,
            long seed) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId must not be null");
        this.partCode = Objects.requireNonNull(partCode, "partCode must not be null");
        this.partName = Objects.requireNonNull(partName, "partName must not be null");
        this.ord = ord;
        this.durationMinutes = durationMinutes;
        this.questionCountRequired = questionCountRequired;
        this.maxPoints = maxPoints;
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.seed = seed;
        this.generationTries = 0;
    }

    public static AttemptPart createPending(
            UUID id,
            UUID attemptId,
            String partCode,
            String partName,
            int ord,
            Integer durationMinutes,
            Integer questionCountRequired,
            BigDecimal maxPoints,
            long seed) {
        return new AttemptPart(
                id, attemptId, partCode, partName, ord, durationMinutes,
                questionCountRequired, maxPoints, PartState.PENDING, seed);
    }

    public void open(Instant startedAt, Instant deadlineAt) {
        if (this.state != PartState.PENDING && this.state != PartState.GENERATING) {
            throw new IllegalStateException("Cannot open part from state " + this.state);
        }
        if (this.state == PartState.GENERATING) {
            this.generationTries = 0;
            this.generationError = null;
            this.generatingSince = null;
            this.generationToken = null;
        }
        this.state = PartState.OPEN;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.deadlineAt = deadlineAt;
    }

    /** Transitions a pending after-start choice part to {@link PartState#AWAITING_CHOICE} (LT §6.1, L4). */
    public void awaitChoice(Instant choiceDeadlineAt) {
        if (this.state != PartState.PENDING) {
            throw new IllegalStateException("Cannot await choice from state " + this.state);
        }
        this.state = PartState.AWAITING_CHOICE;
        this.choiceDeadlineAt =
                Objects.requireNonNull(choiceDeadlineAt, "choiceDeadlineAt must not be null");
    }

    /** Locks the student's part choice and starts background generation (LT §6.1, L8). */
    public void confirmChoiceAndStartGenerating(
            Map<String, Object> choice,
            Instant confirmedAt,
            UUID generationToken,
            Instant generatingSince) {
        if (this.state != PartState.AWAITING_CHOICE) {
            throw new IllegalStateException("Cannot confirm choice from state " + this.state);
        }
        Objects.requireNonNull(choice, "choice must not be null");
        this.choice = Map.copyOf(choice);
        this.choiceConfirmedAt =
                Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");
        this.state = PartState.GENERATING;
        this.generationToken =
                Objects.requireNonNull(generationToken, "generationToken must not be null");
        this.generatingSince =
                Objects.requireNonNull(generatingSince, "generatingSince must not be null");
        this.generationError = null;
    }

    /** Records a generation failure and increments {@code generationTries} (LT §6.1). */
    public void failGeneration(String error) {
        if (this.state != PartState.GENERATING) {
            throw new IllegalStateException("Cannot fail generation from state " + this.state);
        }
        this.state = PartState.GENERATION_FAILED;
        this.generationTries = this.generationTries + 1;
        this.generationError = error;
        this.generatingSince = null;
        this.generationToken = null;
    }

    /** Moves a failed part back to {@link PartState#GENERATING} for manual or worker retry (LT §6.1). */
    public void startRetryGenerating(UUID generationToken, Instant generatingSince) {
        if (this.state != PartState.GENERATION_FAILED) {
            throw new IllegalStateException("Cannot start retry from state " + this.state);
        }
        this.state = PartState.GENERATING;
        this.generationToken =
                Objects.requireNonNull(generationToken, "generationToken must not be null");
        this.generatingSince =
                Objects.requireNonNull(generatingSince, "generatingSince must not be null");
        this.generationError = null;
    }

    public void close(PartCloseReason reason, Instant closedAt) {
        if (this.state != PartState.OPEN) {
            throw new IllegalStateException("Cannot close part from state " + this.state);
        }
        this.state = PartState.CLOSED;
        this.closeReason = Objects.requireNonNull(reason, "reason must not be null");
        this.closedAt = Objects.requireNonNull(closedAt, "closedAt must not be null");
    }

    public void skip(PartCloseReason reason, Instant closedAt) {
        if (this.state == PartState.OPEN || this.state == PartState.CLOSED) {
            throw new IllegalStateException("Cannot skip part from state " + this.state);
        }
        this.state = PartState.SKIPPED;
        this.closeReason = reason;
        this.closedAt = closedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public String getPartCode() {
        return partCode;
    }

    public String getPartName() {
        return partName;
    }

    public int getOrd() {
        return ord;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public Integer getQuestionCountRequired() {
        return questionCountRequired;
    }

    public BigDecimal getMaxPoints() {
        return maxPoints;
    }

    public PartState getState() {
        return state;
    }

    public Map<String, Object> getChoice() {
        return choice;
    }

    public Instant getChoiceConfirmedAt() {
        return choiceConfirmedAt;
    }

    public Instant getChoiceDeadlineAt() {
        return choiceDeadlineAt;
    }

    public long getSeed() {
        return seed;
    }

    public Instant getGeneratingSince() {
        return generatingSince;
    }

    public UUID getGenerationToken() {
        return generationToken;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public PartCloseReason getCloseReason() {
        return closeReason;
    }

    public int getGenerationTries() {
        return generationTries;
    }

    public String getGenerationError() {
        return generationError;
    }

    public Long getVersion() {
        return version;
    }
}
