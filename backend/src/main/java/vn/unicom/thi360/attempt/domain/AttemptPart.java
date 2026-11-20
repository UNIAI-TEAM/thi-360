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
        this.state = PartState.OPEN;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.deadlineAt = deadlineAt;
    }

    public void close(PartCloseReason reason, Instant closedAt) {
        this.state = PartState.CLOSED;
        this.closeReason = Objects.requireNonNull(reason, "reason must not be null");
        this.closedAt = Objects.requireNonNull(closedAt, "closedAt must not be null");
    }

    public void skip(PartCloseReason reason, Instant closedAt) {
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
