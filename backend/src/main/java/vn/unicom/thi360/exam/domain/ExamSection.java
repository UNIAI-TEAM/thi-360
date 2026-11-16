package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamSection(
        String id,
        String subjectCode,
        Integer questionCount,
        KnowledgeScope knowledgeScope,
        AnswerQuotas answerQuotas,
        BlockMatrix matrix,
        GroupOrder groupOrder,
        List<QuestionBlock> blocks) {
    public ExamSection {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
