package vn.unicom.thi360.exam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "exam_block_ref", schema = "exam")
public class ExamBlockRef {
    @Id
    private UUID id;
    @Column(name = "exam_id", nullable = false)
    private UUID examId;
    @Column(name = "version_no")
    private Integer versionNo;
    @Column(name = "block_id", nullable = false)
    private String blockId;
    @Column(name = "part_id", nullable = false)
    private String partId;
    @Column(name = "option_code")
    private String optionCode;
    @Column(name = "section_id")
    private String sectionId;
    @Column(name = "subject_item_id")
    private UUID subjectItemId;
    @Column(name = "question_type")
    private String questionType;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "answer_types", columnDefinition = "text[]")
    private List<String> answerTypes;
    @Column(name = "task_type_item_id")
    private UUID taskTypeItemId;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "knowledge_item_ids", columnDefinition = "uuid[]")
    private List<UUID> knowledgeItemIds;
    @Column(name = "knowledge_mode")
    private String knowledgeMode;
    @Column(name = "knowledge_inherited", nullable = false)
    private boolean knowledgeInherited;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tag_item_ids", columnDefinition = "uuid[]")
    private List<UUID> tagItemIds;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "competency_item_ids", columnDefinition = "uuid[]")
    private List<UUID> competencyItemIds;
    @Column(name = "required_points", precision = 8, scale = 4)
    private BigDecimal requiredPoints;
    @Column(name = "combination_item_id")
    private UUID combinationItemId;

    protected ExamBlockRef() {
    }

    public ExamBlockRef(UUID id, UUID examId, Integer versionNo, String blockId, String partId, String optionCode,
            String sectionId, UUID subjectItemId, String questionType, List<String> answerTypes, UUID taskTypeItemId,
            List<UUID> knowledgeItemIds, String knowledgeMode, boolean knowledgeInherited, List<UUID> tagItemIds,
            List<UUID> competencyItemIds, BigDecimal requiredPoints, UUID combinationItemId) {
        this.id = id;
        this.examId = examId;
        this.versionNo = versionNo;
        this.blockId = blockId;
        this.partId = partId;
        this.optionCode = optionCode;
        this.sectionId = sectionId;
        this.subjectItemId = subjectItemId;
        this.questionType = questionType;
        this.answerTypes = answerTypes == null ? null : List.copyOf(answerTypes);
        this.taskTypeItemId = taskTypeItemId;
        this.knowledgeItemIds = knowledgeItemIds == null ? null : List.copyOf(knowledgeItemIds);
        this.knowledgeMode = knowledgeMode;
        this.knowledgeInherited = knowledgeInherited;
        this.tagItemIds = tagItemIds == null ? null : List.copyOf(tagItemIds);
        this.competencyItemIds = competencyItemIds == null ? null : List.copyOf(competencyItemIds);
        this.requiredPoints = requiredPoints;
        this.combinationItemId = combinationItemId;
    }

    public UUID getId() { return id; }
    public UUID getExamId() { return examId; }
    public Integer getVersionNo() { return versionNo; }
    public String getBlockId() { return blockId; }
    public String getPartId() { return partId; }
    public String getOptionCode() { return optionCode; }
    public String getSectionId() { return sectionId; }
    public UUID getSubjectItemId() { return subjectItemId; }
    public String getQuestionType() { return questionType; }
    public List<String> getAnswerTypes() { return answerTypes; }
    public UUID getTaskTypeItemId() { return taskTypeItemId; }
    public List<UUID> getKnowledgeItemIds() { return knowledgeItemIds; }
    public String getKnowledgeMode() { return knowledgeMode; }
    public boolean isKnowledgeInherited() { return knowledgeInherited; }
    public List<UUID> getTagItemIds() { return tagItemIds; }
    public List<UUID> getCompetencyItemIds() { return competencyItemIds; }
    public BigDecimal getRequiredPoints() { return requiredPoints; }
    public UUID getCombinationItemId() { return combinationItemId; }
}
