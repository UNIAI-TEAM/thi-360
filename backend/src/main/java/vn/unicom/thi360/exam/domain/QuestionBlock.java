package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("shuffle")
public record QuestionBlock(
        String id,
        String name,
        QuestionType questionType,
        Integer questionCount,
        Integer setCount,
        Integer setSize,
        KnowledgeScope knowledgeScope,
        BlockMatrix matrix,
        String taskTypeCode,
        QuestionType childAnswerType,
        String templateGroupCode,
        Boolean answerTypeLocked,
        BigDecimal requiredPointsPerQuestion,
        List<String> tagCodes,
        BlockExpectedScoring expectedScoring) {
    public QuestionBlock {
        tagCodes = tagCodes == null ? List.of() : List.copyOf(tagCodes);
    }
}
