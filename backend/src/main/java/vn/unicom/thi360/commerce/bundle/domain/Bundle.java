package vn.unicom.thi360.commerce.bundle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Working copy of an exam bundle ({@code commerce.bundle}, TM 16.1).
 *
 * <p>{@code revision} is bumped explicitly under row lock on every admin mutation ({@code update}, {@code publish},
 * {@code discontinue}) so concurrent editors receive 412 without altering frozen published versions.
 */
@Entity
@Table(name = "bundle", schema = "commerce")
public class Bundle {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "code_normalized", nullable = false)
    private String codeNormalized;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "summary")
    private String summary;

    @Column(name = "description")
    private String description;

    @Column(name = "exam_kind_item_id", nullable = false)
    private UUID examKindItemId;

    @Column(name = "list_price_vnd")
    private Long listPriceVnd;

    @Column(name = "attempts_per_exam")
    private Integer attemptsPerExam;

    @Column(name = "benefit_ai_explanation", nullable = false)
    private boolean benefitAiExplanation;

    @Column(name = "benefit_strength_weakness", nullable = false)
    private boolean benefitStrengthWeakness;

    @Column(name = "benefit_practice", nullable = false)
    private boolean benefitPractice;

    @Column(name = "benefit_note")
    private String benefitNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_view", nullable = false)
    private BundleResultView resultView;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BundleStatus status;

    @Column(name = "has_unpublished_changes", nullable = false)
    private boolean hasUnpublishedChanges;

    @Column(name = "serving_version")
    private Integer servingVersion;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_by_username", updatable = false)
    private String createdByUsername;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_by_username")
    private String updatedByUsername;

    @Column(name = "discontinued_at")
    private Instant discontinuedAt;

    @Column(name = "discontinued_by")
    private UUID discontinuedBy;

    protected Bundle() {
    }

    public static Bundle createDraft(
            UUID id,
            String code,
            String name,
            String summary,
            String description,
            UUID examKindItemId,
            Long listPriceVnd,
            Integer attemptsPerExam,
            boolean benefitAiExplanation,
            boolean benefitStrengthWeakness,
            boolean benefitPractice,
            String benefitNote,
            BundleResultView resultView,
            Instant now,
            UUID actorId,
            String actorUsername) {
        Bundle b = new Bundle();
        b.id = id;
        b.code = code.trim();
        b.codeNormalized = BundleCodeNormalizer.normalize(code);
        b.name = name.trim();
        b.summary = summary;
        b.description = description;
        b.examKindItemId = examKindItemId;
        b.listPriceVnd = listPriceVnd;
        b.attemptsPerExam = attemptsPerExam;
        b.benefitAiExplanation = benefitAiExplanation;
        b.benefitStrengthWeakness = benefitStrengthWeakness;
        b.benefitPractice = benefitPractice;
        b.benefitNote = benefitNote;
        b.resultView = resultView == null ? BundleResultView.SCORE_AND_ANSWERS : resultView;
        b.status = BundleStatus.DRAFT;
        b.hasUnpublishedChanges = false;
        b.servingVersion = null;
        b.revision = 0L;
        b.createdAt = now;
        b.createdBy = actorId;
        b.createdByUsername = actorUsername;
        b.updatedAt = now;
        b.updatedBy = actorId;
        b.updatedByUsername = actorUsername;
        return b;
    }

    public void updateWorkingCopy(
            String code,
            String name,
            String summary,
            String description,
            UUID examKindItemId,
            Long listPriceVnd,
            Integer attemptsPerExam,
            boolean benefitAiExplanation,
            boolean benefitStrengthWeakness,
            boolean benefitPractice,
            String benefitNote,
            BundleResultView resultView,
            Instant now,
            UUID actorId,
            String actorUsername) {
        this.code = code.trim();
        this.codeNormalized = BundleCodeNormalizer.normalize(code);
        this.name = name.trim();
        this.summary = summary;
        this.description = description;
        this.examKindItemId = examKindItemId;
        this.listPriceVnd = listPriceVnd;
        this.attemptsPerExam = attemptsPerExam;
        this.benefitAiExplanation = benefitAiExplanation;
        this.benefitStrengthWeakness = benefitStrengthWeakness;
        this.benefitPractice = benefitPractice;
        this.benefitNote = benefitNote;
        this.resultView = resultView == null ? BundleResultView.SCORE_AND_ANSWERS : resultView;
        if (this.status == BundleStatus.PUBLISHED) {
            this.hasUnpublishedChanges = true;
        }
        this.revision = this.revision + 1L;
        this.updatedAt = now;
        this.updatedBy = actorId;
        this.updatedByUsername = actorUsername;
    }

    public void markPublished(int versionNo, Instant now, UUID actorId, String actorUsername) {
        this.status = BundleStatus.PUBLISHED;
        this.servingVersion = versionNo;
        this.hasUnpublishedChanges = false;
        this.revision = this.revision + 1L;
        this.updatedAt = now;
        this.updatedBy = actorId;
        this.updatedByUsername = actorUsername;
    }

    public void markDiscontinued(Instant now, UUID actorId, String actorUsername) {
        this.status = BundleStatus.DISCONTINUED;
        this.discontinuedAt = now;
        this.discontinuedBy = actorId;
        this.revision = this.revision + 1L;
        this.updatedAt = now;
        this.updatedBy = actorId;
        this.updatedByUsername = actorUsername;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getCodeNormalized() {
        return codeNormalized;
    }

    public String getName() {
        return name;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public UUID getExamKindItemId() {
        return examKindItemId;
    }

    public Long getListPriceVnd() {
        return listPriceVnd;
    }

    public Integer getAttemptsPerExam() {
        return attemptsPerExam;
    }

    public boolean isBenefitAiExplanation() {
        return benefitAiExplanation;
    }

    public boolean isBenefitStrengthWeakness() {
        return benefitStrengthWeakness;
    }

    public boolean isBenefitPractice() {
        return benefitPractice;
    }

    public String getBenefitNote() {
        return benefitNote;
    }

    public BundleResultView getResultView() {
        return resultView;
    }

    public BundleStatus getStatus() {
        return status;
    }

    public boolean isHasUnpublishedChanges() {
        return hasUnpublishedChanges;
    }

    public Integer getServingVersion() {
        return servingVersion;
    }

    public long getRevision() {
        return revision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public String getUpdatedByUsername() {
        return updatedByUsername;
    }

    public Instant getDiscontinuedAt() {
        return discontinuedAt;
    }

    public UUID getDiscontinuedBy() {
        return discontinuedBy;
    }
}
