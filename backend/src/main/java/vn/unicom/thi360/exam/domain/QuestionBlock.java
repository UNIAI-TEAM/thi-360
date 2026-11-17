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
        QuestionType childAnswerType,
        String templateGroupCode,
        Boolean answerTypeLocked,
        BigDecimal requiredPointsPerQuestion,
        List<String> tagCodes,
        BlockExpectedScoring expectedScoring,
        Boolean shuffleUnits,
        Boolean shuffleEditable,
        List<BlockSlot> slots,
        List<ChildCondition> childConditions,
        ItemShape itemShape,
        List<String> answerFormats,
        Boolean omitted,
        List<TopicQuota> topicQuotas,
        PositionRange positionRange) {
    public QuestionBlock {
        shuffleUnits = shuffleUnits == null ? true : shuffleUnits;
        shuffleEditable = shuffleEditable == null ? false : shuffleEditable;
        omitted = omitted == null ? false : omitted;
        tagCodes = tagCodes == null ? List.of() : List.copyOf(tagCodes);
        slots = slots == null ? List.of() : List.copyOf(slots);
        childConditions = childConditions == null ? List.of() : List.copyOf(childConditions);
        answerFormats = answerFormats == null ? List.of() : List.copyOf(answerFormats);
        topicQuotas = topicQuotas == null ? List.of() : List.copyOf(topicQuotas);
    }

    /** Compatibility constructor for the pre-v2 shape; taskTypeCode is deliberately discarded. */
    public QuestionBlock(String id, String name, QuestionType questionType, Integer questionCount,
            Integer setCount, Integer setSize, KnowledgeScope knowledgeScope, BlockMatrix matrix,
            String taskTypeCode, QuestionType childAnswerType, String templateGroupCode,
            Boolean answerTypeLocked, BigDecimal requiredPointsPerQuestion, List<String> tagCodes,
            BlockExpectedScoring expectedScoring) {
        this(id, name, questionType, questionCount, setCount, setSize, knowledgeScope, matrix,
                childAnswerType, templateGroupCode, answerTypeLocked, requiredPointsPerQuestion,
                tagCodes, expectedScoring, true, false, List.of(), List.of(), null, List.of(),
                false, List.of(), null);
    }
}
