package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "exam_kind_profile", schema = "exam")
public class ExamKindProfile {
    @Id
    @Column(name = "exam_item_id", nullable = false)
    private UUID examItemId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "scopes", nullable = false, columnDefinition = "text[]")
    private String[] scopes;

    @Column(name = "template_by", nullable = false)
    private String templateBy;

    @Column(nullable = false)
    private String timing;

    @Column(name = "max_score", precision = 10, scale = 4)
    private BigDecimal maxScore;

    @Column(name = "reference_max", precision = 10, scale = 4)
    private BigDecimal referenceMax;

    @Column(name = "practice_scoring", nullable = false)
    private String practiceScoring;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tf_scoring", columnDefinition = "jsonb")
    private JsonNode tfScoring;

    @Column(name = "mc_rule")
    private String multipleChoiceRule;

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

    protected ExamKindProfile() {
    }

    public ExamKindProfile(UUID examItemId, List<String> scopes, String templateBy, String timing,
            BigDecimal maxScore, BigDecimal referenceMax, String practiceScoring, JsonNode tfScoring,
            String multipleChoiceRule, Instant now, UUID actorId) {
        this.examItemId = examItemId;
        this.scopes = scopes == null ? new String[0] : scopes.toArray(String[]::new);
        this.templateBy = templateBy;
        this.timing = timing;
        this.maxScore = maxScore;
        this.referenceMax = referenceMax;
        this.practiceScoring = practiceScoring;
        this.tfScoring = tfScoring;
        this.multipleChoiceRule = multipleChoiceRule;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public UUID getExamItemId() { return examItemId; }
    public List<String> getScopes() { return scopes == null ? List.of() : List.copyOf(Arrays.asList(scopes)); }
    public String getTiming() { return timing; }
    public BigDecimal getMaxScore() { return maxScore; }
    public BigDecimal getReferenceMax() { return referenceMax; }
    public String getPracticeScoring() { return practiceScoring; }
    public JsonNode getTfScoring() { return tfScoring; }
    public String getMultipleChoiceRule() { return multipleChoiceRule; }
    public String getTemplateBy() { return templateBy; }

    /** PUT thay thế toàn bộ hồ sơ (spec 6); revision tăng nhờ @Version. */
    public void replace(List<String> newScopes, String newTemplateBy, String newTiming,
            BigDecimal newMaxScore, BigDecimal newReferenceMax, String newPracticeScoring,
            JsonNode newTfScoring, String newMultipleChoiceRule, Instant now, UUID actorId) {
        this.scopes = newScopes == null ? new String[0] : newScopes.toArray(String[]::new);
        this.templateBy = newTemplateBy;
        this.timing = newTiming;
        this.maxScore = newMaxScore;
        this.referenceMax = newReferenceMax;
        this.practiceScoring = newPracticeScoring;
        this.tfScoring = newTfScoring;
        this.multipleChoiceRule = newMultipleChoiceRule;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Long getRevision() { return revision; }
}
