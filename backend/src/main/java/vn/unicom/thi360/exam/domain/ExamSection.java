package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("groupOrder")
public record ExamSection(
        String id,
        String subjectCode,
        Integer questionCount,
        KnowledgeScope knowledgeScope,
        AnswerQuotas answerQuotas,
        BlockMatrix matrix,
        ChoiceGroup choiceGroup,
        List<QuestionBlock> blocks,
        List<TopicQuota> topicQuotas,
        AudioRequirement audioRequirement,
        MapidMode mapidMode,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<MapidRule> mapidRules,
        Boolean mixBlocks) {
    public ExamSection {
        blocks = blocks == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(blocks));
        topicQuotas = topicQuotas == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(topicQuotas));
        mapidRules = mapidRules == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(mapidRules));
        mixBlocks = Boolean.TRUE.equals(mixBlocks) ? Boolean.TRUE : null;
    }

    @JsonIgnore
    public boolean isMixBlocks() {
        return Boolean.TRUE.equals(mixBlocks);
    }

    public ExamSection(String id, String subjectCode, Integer questionCount,
            KnowledgeScope knowledgeScope, AnswerQuotas answerQuotas, BlockMatrix matrix,
            ChoiceGroup choiceGroup, List<QuestionBlock> blocks, List<TopicQuota> topicQuotas,
            AudioRequirement audioRequirement, MapidMode mapidMode, List<MapidRule> mapidRules) {
        this(id, subjectCode, questionCount, knowledgeScope, answerQuotas, matrix, choiceGroup,
                blocks, topicQuotas, audioRequirement, mapidMode, mapidRules, null);
    }

    /** Without {@code mapidMode} and {@code mapidRules}: legacy scope and quotas apply. */
    public ExamSection(String id, String subjectCode, Integer questionCount,
            KnowledgeScope knowledgeScope, AnswerQuotas answerQuotas, BlockMatrix matrix,
            ChoiceGroup choiceGroup, List<QuestionBlock> blocks, List<TopicQuota> topicQuotas,
            AudioRequirement audioRequirement) {
        this(id, subjectCode, questionCount, knowledgeScope, answerQuotas, matrix, choiceGroup,
                blocks, topicQuotas, audioRequirement, null, null, null);
    }

    public ExamSection(String id, String subjectCode, Integer questionCount,
            KnowledgeScope knowledgeScope, AnswerQuotas answerQuotas, BlockMatrix matrix,
            ChoiceGroup choiceGroup, List<QuestionBlock> blocks, List<TopicQuota> topicQuotas) {
        this(id, subjectCode, questionCount, knowledgeScope, answerQuotas, matrix, choiceGroup,
                blocks, topicQuotas, null);
    }

    public ExamSection(String id, String subjectCode, Integer questionCount,
            KnowledgeScope knowledgeScope, AnswerQuotas answerQuotas, BlockMatrix matrix,
            ChoiceGroup choiceGroup, List<QuestionBlock> blocks) {
        this(id, subjectCode, questionCount, knowledgeScope, answerQuotas, matrix, choiceGroup,
                blocks, List.of(), null);
    }
}
