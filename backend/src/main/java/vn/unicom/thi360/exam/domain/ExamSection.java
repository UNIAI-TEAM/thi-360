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
        List<QuestionBlock> blocks) {
    public ExamSection {
        blocks = blocks == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(blocks));
    }
}
