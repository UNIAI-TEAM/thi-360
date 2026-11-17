package vn.unicom.thi360.exam.domain;

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
        List<TopicQuota> topicQuotas) {
    public ExamSection {
        blocks = blocks == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(blocks));
        topicQuotas = topicQuotas == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(topicQuotas));
    }

    public ExamSection(String id, String subjectCode, Integer questionCount,
            KnowledgeScope knowledgeScope, AnswerQuotas answerQuotas, BlockMatrix matrix,
            ChoiceGroup choiceGroup, List<QuestionBlock> blocks) {
        this(id, subjectCode, questionCount, knowledgeScope, answerQuotas, matrix, choiceGroup,
                blocks, List.of());
    }
}
