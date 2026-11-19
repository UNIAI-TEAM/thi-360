package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.api.ExamStructureResponse;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;

/**
 * BE-21 lane tests for {@link ExamCheckUnitSummary}: the mixed-set command rule (R1-2, R1-7,
 * moved out of the shared {@code ExamCheckUnitSummaryTests} per R2-6) and the overflow edges
 * R2-4 (remainder after fixed counts that sum above {@code Integer.MAX_VALUE}) and R2-5
 * (SUBJECT_CHOICE counts whose aggregate leaves the int range).
 */
class VsatEnglishUnitSummaryTests {
    private final ExamStructureCodec codec = new ExamStructureCodec(new ObjectMapper());

    /**
     * R1-2: an out-of-range command count is reported as undetermined (null) instead of throwing —
     * per typed range, per block multiplication and in the aggregate.
     */
    @Test
    void unrepresentableCommandCountsAreUndetermined() {
        var perRange = codec.read("""
                {"parts":[{"id":"p","questionCount":2147483647,"sections":[
                  {"id":"s","subjectCode":"ANH","questionCount":2147483647,"blocks":[
                    {"id":"set","questionType":"QUESTION_SET","setCount":1,"setSize":2147483647,
                     "requiredPointsPerQuestion":6,"childConditions":[
                      {"from":1,"to":2147483647,"answerType":"TRUE_FALSE","itemShape":{"statements":4}}]}]}]}]}
                """);
        var set = ExamCheckUnitSummary.summarize(new ExamStructureResponse(UUID.randomUUID(),
                perRange.parts(), 1)).parts().getFirst().children().getFirst().children().getFirst();
        assertThat(set.allocated()).isEqualTo(2147483647);
        assertThat(set.commandCount()).isNull();

        var perBlock = codec.read("""
                {"parts":[{"id":"p","sections":[
                  {"id":"s","subjectCode":"ANH","blocks":[
                    {"id":"set","questionType":"QUESTION_SET","setCount":2147483647,"setSize":2,
                     "requiredPointsPerQuestion":1,"childConditions":[
                      {"from":1,"to":2,"answerType":"SINGLE_CHOICE"}]}]}]}]}
                """);
        var block = ExamCheckUnitSummary.summarize(new ExamStructureResponse(UUID.randomUUID(),
                perBlock.parts(), 1)).parts().getFirst().children().getFirst().children().getFirst();
        assertThat(block.allocated()).isNull();
        assertThat(block.commandCount()).isNull();

        var aggregate = codec.read("""
                {"parts":[{"id":"p","sections":[
                  {"id":"s","subjectCode":"ANH","questionCount":2147483646,"blocks":[
                    {"id":"a","questionType":"SINGLE_CHOICE","questionCount":2147483646,
                     "requiredPointsPerQuestion":1},
                    {"id":"b","questionType":"TRUE_FALSE","questionCount":2147483646,
                     "itemShape":{"statements":4},"requiredPointsPerQuestion":1}]}]}]}
                """);
        var part = ExamCheckUnitSummary.summarize(new ExamStructureResponse(UUID.randomUUID(),
                aggregate.parts(), 1)).parts().getFirst();
        assertThat(part.allocated()).isNull();
        assertThat(part.commandCount()).isNull();
        assertThat(part.children().getFirst().children().getFirst().commandCount())
                .isEqualTo(2147483646);
    }

    /**
     * R1-7: two mixed sets whose typed children hold a four-item MATCHING range and a choice range
     * produce 2 × (4 + 1) = 10 commands in both the block and the part total.
     */
    @Test
    void mixedSetsMultiplyTypedChildrenBySetCount() {
        var document = codec.read("""
                {"parts":[{"id":"p","questionCount":4,"sections":[
                  {"id":"s","subjectCode":"ANH","questionCount":4,"blocks":[
                    {"id":"set","questionType":"QUESTION_SET","setCount":2,"setSize":2,
                     "requiredPointsPerQuestion":6,"childConditions":[
                      {"from":1,"to":1,"answerType":"MATCHING","itemShape":{"leftItems":4}},
                      {"from":2,"to":2,"answerType":"SINGLE_CHOICE"}]}]}]}]}
                """);
        var part = ExamCheckUnitSummary.summarize(new ExamStructureResponse(UUID.randomUUID(),
                document.parts(), 1)).parts().getFirst();
        var block = part.children().getFirst().children().getFirst();
        assertThat(block.allocated()).isEqualTo(4);
        assertThat(block.commandCount()).isEqualTo(10);
        assertThat(part.commandCount()).isEqualTo(10);
    }

    /**
     * R2-4: a remainder row whose sibling fixed counts are individually representable but sum above
     * {@code Integer.MAX_VALUE} reports an undetermined remainder instead of overflowing.
     */
    @Test
    void remainderIsUndeterminedWhenFixedCountsSumAboveIntRange() {
        var document = codec.read("""
                {"parts":[{"id":"p","sections":[
                  {"id":"s","subjectCode":"ANH","questionCount":2147483647,"blocks":[
                    {"id":"a","questionType":"SINGLE_CHOICE","questionCount":1500000000,
                     "requiredPointsPerQuestion":1},
                    {"id":"b","questionType":"SINGLE_CHOICE","questionCount":1000000000,
                     "requiredPointsPerQuestion":1},
                    {"id":"rest","questionType":"SINGLE_CHOICE","requiredPointsPerQuestion":1}]}]}]}
                """);
        var section = ExamCheckUnitSummary.summarize(new ExamStructureResponse(UUID.randomUUID(),
                document.parts(), 1)).parts().getFirst().children().getFirst();
        assertThat(section.children().get(0).allocated()).isEqualTo(1500000000);
        assertThat(section.children().get(1).allocated()).isEqualTo(1000000000);
        var remainder = section.children().get(2);
        assertThat(remainder.allocated()).isNull();
        assertThat(remainder.commandCount()).isNull();
        assertThat(section.allocated()).isNull();
    }

    /**
     * R2-5: a SUBJECT_CHOICE whose pick counts [1500000000, 1499999999] push the selected total out
     * of the int range reports an unknown aggregate; the section allocations are unaffected.
     */
    @Test
    void subjectChoiceWithOutOfRangeAggregateIsUndetermined() {
        var document = codec.read("""
                {"parts":[{"id":"p","questionCount":1500000000,
                  "participation":{"mode":"CHOICE","choose":1},"options":[
                    {"id":"o","kind":"SUBJECT_CHOICE","chooseSubjects":2,
                     "countsByPick":[1500000000,1499999999],"sections":[
                      {"id":"sa","subjectCode":"A","questionCount":1500000000,"blocks":[
                        {"id":"a","questionType":"SINGLE_CHOICE","questionCount":1500000000,
                         "requiredPointsPerQuestion":1}]},
                      {"id":"sb","subjectCode":"B","questionCount":1500000000,"blocks":[
                        {"id":"b","questionType":"SINGLE_CHOICE","questionCount":1499999999,
                         "requiredPointsPerQuestion":1}]}]}]}]}
                """);
        var part = ExamCheckUnitSummary.summarize(new ExamStructureResponse(UUID.randomUUID(),
                document.parts(), 1)).parts().getFirst();
        assertThat(part.allocated()).isNull();
        assertThat(part.commandCount()).isNull();
        assertThat(part.children().get(0).allocated()).isEqualTo(1500000000);
        assertThat(part.children().get(1).allocated()).isEqualTo(1499999999);
    }
}
