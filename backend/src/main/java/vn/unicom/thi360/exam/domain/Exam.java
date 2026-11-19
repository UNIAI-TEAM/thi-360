package vn.unicom.thi360.exam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam", schema = "exam")
public class Exam {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String code;
    @Column(name = "code_normalized", nullable = false)
    private String codeNormalized;
    @Column(nullable = false)
    private String name;
    private String description;
    private String instructions;
    @Column(name = "kind_item_id")
    private UUID kindItemId;
    @Column(nullable = false)
    private String scope;
    @Column(name = "part_code")
    private String partCode;
    @Column(name = "subject_item_id")
    private UUID subjectItemId;
    @Column(name = "template_code")
    private String templateCode;
    @Column(name = "build_mode", nullable = false)
    private String buildMode = "STANDARD";
    @Column(name = "structure_year")
    private Integer structureYear;
    @Column(name = "matrix_year")
    private Integer matrixYear;
    @Column(name = "years_confirmed", nullable = false)
    private boolean yearsConfirmed;
    @Column(nullable = false)
    private String timing;
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    @Column(name = "max_score", precision = 10, scale = 4)
    private BigDecimal maxScore;
    @Column(name = "reference_max", precision = 10, scale = 4)
    private BigDecimal referenceMax;
    @Column(name = "practice_scoring", nullable = false)
    private String practiceScoring;
    @Column(nullable = false)
    private String status;
    @Column(name = "serving_version")
    private Integer servingVersion;
    @Column(name = "check_status", nullable = false)
    private String checkStatus;
    @Column(name = "check_issue_count", nullable = false)
    private int checkIssueCount;
    @Column(name = "checked_at")
    private Instant checkedAt;
    @Column(name = "cover_asset_id")
    private UUID coverAssetId;
    @Column(name = "has_unpublished_changes", nullable = false)
    private boolean hasUnpublishedChanges;
    @Column(name = "question_count", nullable = false)
    private int questionCount;
    @Column(name = "has_essay", nullable = false)
    private boolean hasEssay;
    @Column(name = "has_english_option", nullable = false)
    private boolean hasEnglishOption;
    @Column(name = "combination_count", nullable = false)
    private int combinationCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Version
    @Column(nullable = false)
    private Long revision;

    protected Exam() {
    }

    public Exam(UUID id, String code, String name, UUID kindItemId, String scope, String timing,
            Integer durationMinutes, BigDecimal maxScore, String practiceScoring, Instant now, UUID actorId) {
        this.id = id;
        this.code = code;
        this.codeNormalized = code == null ? null : code.trim().toLowerCase(java.util.Locale.ROOT);
        this.name = name;
        this.kindItemId = kindItemId;
        this.scope = scope;
        this.timing = timing;
        this.durationMinutes = durationMinutes;
        this.maxScore = maxScore;
        this.practiceScoring = practiceScoring;
        this.status = ExamStatus.DRAFT.name();
        this.checkStatus = ExamCheckStatus.UNCHECKED.name();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getCodeNormalized() { return codeNormalized; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getInstructions() { return instructions; }
    public UUID getKindItemId() { return kindItemId; }
    public String getScope() { return scope; }
    public String getPartCode() { return partCode; }
    public UUID getSubjectItemId() { return subjectItemId; }
    public String getTemplateCode() { return templateCode; }
    public String getBuildMode() { return buildMode; }
    public void setBuildMode(String buildMode) { this.buildMode = buildMode; }
    public Integer getStructureYear() { return structureYear; }
    public Integer getMatrixYear() { return matrixYear; }
    public boolean isYearsConfirmed() { return yearsConfirmed; }
    public String getTiming() { return timing; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public BigDecimal getMaxScore() { return maxScore; }
    public BigDecimal getReferenceMax() { return referenceMax; }
    public String getPracticeScoring() { return practiceScoring; }
    public String getStatus() { return status; }
    public Integer getServingVersion() { return servingVersion; }
    public String getCheckStatus() { return checkStatus; }
    public int getCheckIssueCount() { return checkIssueCount; }
    public Instant getCheckedAt() { return checkedAt; }
    public UUID getCoverAssetId() { return coverAssetId; }
    public boolean isHasUnpublishedChanges() { return hasUnpublishedChanges; }
    public int getQuestionCount() { return questionCount; }
    public boolean isHasEssay() { return hasEssay; }
    public boolean isHasEnglishOption() { return hasEnglishOption; }
    public int getCombinationCount() { return combinationCount; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Long getRevision() { return revision; }

    // Setter dạng PATCH: dịch vụ tính giá trị hiệu lực (kể cả xóa về null); entity chỉ gán + touch.
    public void updateInfo(String code, String name, String description, String instructions,
            Integer durationMinutes, BigDecimal maxScore, UUID actorId, Instant now) {
        this.code = code;
        this.codeNormalized = code == null ? null : code.trim().toLowerCase(java.util.Locale.ROOT);
        this.name = name;
        this.description = description;
        this.instructions = instructions;
        this.durationMinutes = durationMinutes;
        this.maxScore = maxScore;
        touch(actorId, now);
    }

    public void applyScoringProfile(BigDecimal referenceMax, String practiceScoring) {
        this.referenceMax = referenceMax;
        this.practiceScoring = practiceScoring;
    }

    /** Đổi phạm vi (kỳ thi/phạm vi/phần/môn/năm) sau khi dịch vụ đã nạp lại mẫu tương ứng. */
    public void changeScope(UUID kindItemId, String scope, String partCode, UUID subjectItemId,
            Integer structureYear, Integer matrixYear, boolean yearsConfirmed,
            String templateCode, String timing, Integer durationMinutes, BigDecimal maxScore,
            UUID actorId, Instant now) {
        this.kindItemId = kindItemId;
        this.scope = scope;
        this.partCode = partCode;
        this.subjectItemId = subjectItemId;
        this.structureYear = structureYear;
        this.matrixYear = matrixYear;
        this.yearsConfirmed = yearsConfirmed;
        this.templateCode = templateCode;
        this.timing = timing;
        this.durationMinutes = durationMinutes;
        this.maxScore = maxScore;
        touch(actorId, now);
    }

    /** Sao chép toàn bộ nội dung từ bài nguồn (nhân bản); id/code/name/status/timestamps giữ của bản này. */
    public void copyDetailsFrom(Exam source) {
        this.description = source.description;
        this.instructions = source.instructions;
        this.kindItemId = source.kindItemId;
        this.scope = source.scope;
        this.partCode = source.partCode;
        this.subjectItemId = source.subjectItemId;
        this.templateCode = source.templateCode;
        this.buildMode = source.buildMode;
        this.structureYear = source.structureYear;
        this.matrixYear = source.matrixYear;
        this.yearsConfirmed = source.yearsConfirmed;
        this.timing = source.timing;
        this.durationMinutes = source.durationMinutes;
        this.maxScore = source.maxScore;
        this.referenceMax = source.referenceMax;
        this.practiceScoring = source.practiceScoring;
        this.questionCount = source.questionCount;
        this.hasEssay = source.hasEssay;
        this.hasEnglishOption = source.hasEnglishOption;
        this.combinationCount = source.combinationCount;
        this.hasUnpublishedChanges = true;
    }

    /** Ghi nhận mẫu vừa nạp: mã mẫu, năm cấu trúc/ma trận, timing và (nếu mẫu khai báo) thời lượng + thang điểm. */
    public void applyTemplate(String templateCode, Integer structureYear, Integer matrixYear,
            String timing, Integer durationMinutes, BigDecimal maxScore, UUID actorId, Instant now) {
        this.templateCode = templateCode;
        this.structureYear = structureYear;
        this.matrixYear = matrixYear;
        this.yearsConfirmed = structureYear == null || structureYear.equals(matrixYear);
        if (timing != null) {
            this.timing = timing;
        }
        if (durationMinutes != null) {
            this.durationMinutes = durationMinutes;
        }
        if (maxScore != null) {
            this.maxScore = maxScore;
        }
        touch(actorId, now);
    }

    public void setYearsConfirmed(boolean confirmed) {
        this.yearsConfirmed = confirmed;
    }

    /** Cấu trúc thay đổi sau lần kiểm tra gần nhất → cần kiểm lại (spec 11.3), trừ khi chưa từng kiểm. */
    public void markStructureChanged(UUID actorId, Instant now) {
        this.hasUnpublishedChanges = true;
        if (!ExamCheckStatus.UNCHECKED.name().equals(this.checkStatus)) {
            this.checkStatus = ExamCheckStatus.NEEDS_RECHECK.name();
        }
        touch(actorId, now);
    }

    /**
     * Sale listing edited (TM 6.1): the description and cover live on this row, the rest in exam_listing. The
     * listing is not part of the check fingerprint, so the check status stays; the revision still moves (@Version).
     */
    public void changeListing(String newDescription, UUID newCoverAssetId, UUID actorId, Instant now) {
        this.description = newDescription;
        this.coverAssetId = newCoverAssetId;
        this.hasUnpublishedChanges = true;
        touch(actorId, now);
    }

    /** Tính lại các cột tổng hợp cho danh sách/thẻ bài thi sau khi cấu trúc đổi. */
    public void applyStructureSummary(int newQuestionCount, boolean newHasEssay,
            boolean newHasEnglishOption, int newCombinationCount) {
        this.questionCount = newQuestionCount;
        this.hasEssay = newHasEssay;
        this.hasEnglishOption = newHasEnglishOption;
        this.combinationCount = newCombinationCount;
    }

    private void touch(UUID actorId, Instant now) {
        this.updatedBy = actorId;
        this.updatedAt = now;
    }
}
