package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;

@Entity
@Table(name = "exam_template", schema = "exam")
public class ExamTemplate {
    @Id
    @Column(nullable = false)
    private String code;
    @Column(name = "kind_code", nullable = false)
    private String kindCode;
    @Column(nullable = false)
    private String scope;
    @Column(name = "part_code")
    private String partCode;
    @Column(name = "subject_code")
    private String subjectCode;
    @Column(name = "structure_year")
    private Integer structureYear;
    @Column(name = "matrix_year")
    private Integer matrixYear;
    @Column(nullable = false)
    private String timing;
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    @Column(name = "max_score", precision = 10, scale = 4)
    private BigDecimal maxScore;
    @Column(name = "expert_note")
    private String expertNote;
    @Column(name = "label")
    private String label;
    @Column(name = "summary")
    private String summary;
    @Column(name = "build_mode", nullable = false)
    private String buildMode = "STANDARD";
    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode structure;
    @Column(nullable = false)
    private boolean active;

    protected ExamTemplate() {
    }

    public ExamTemplate(String code, String kindCode, String scope, String partCode, String subjectCode,
            Integer structureYear, Integer matrixYear, String timing, Integer durationMinutes,
            BigDecimal maxScore, String expertNote, JsonNode structure, boolean active) {
        this.code = code;
        this.kindCode = kindCode;
        this.scope = scope;
        this.partCode = partCode;
        this.subjectCode = subjectCode;
        this.structureYear = structureYear;
        this.matrixYear = matrixYear;
        this.timing = timing;
        this.durationMinutes = durationMinutes;
        this.maxScore = maxScore;
        this.expertNote = expertNote;
        this.structure = structure;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getKindCode() { return kindCode; }
    public String getScope() { return scope; }
    public String getPartCode() { return partCode; }
    public String getSubjectCode() { return subjectCode; }
    public Integer getStructureYear() { return structureYear; }
    public Integer getMatrixYear() { return matrixYear; }
    public String getTiming() { return timing; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public BigDecimal getMaxScore() { return maxScore; }
    public String getExpertNote() { return expertNote; }
    public String getLabel() { return label; }
    public String getSummary() { return summary; }
    public String getBuildMode() { return buildMode; }
    public boolean isDefaultTemplate() { return defaultTemplate; }
    public JsonNode getStructure() { return structure; }
    public boolean isActive() { return active; }
}
