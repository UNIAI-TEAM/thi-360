package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.api.ExamStructureResponse;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;

class ExamCheckUnitSummaryTests {
    private final ExamStructureCodec codec = new ExamStructureCodec(new ObjectMapper());

    @Test
    void reportsOverviewOverageWithoutSolving() {
        var document = codec.read("""
                {"parts":[{"id":"math","name":"Math","questionCount":40,"sections":[
                  {"id":"math-section","subjectCode":"TOAN","questionCount":50,"blocks":[
                    {"id":"single","name":"Single","questionType":"SINGLE_CHOICE", "questionCount":35,
                     "requiredPointsPerQuestion":1},
                    {"id":"short","name":"Short","questionType":"SHORT_ANSWER", "questionCount":15,
                     "requiredPointsPerQuestion":1}]}]}]}
                """);
        var response = ExamCheckUnitSummary.summarize(new ExamStructureResponse(UUID.randomUUID(), document.parts(), 1));
        var part = response.parts().getFirst();
        assertThat(part.target()).isEqualTo(40);
        assertThat(part.allocated()).isEqualTo(50);
        assertThat(part.difference()).isEqualTo(10);
        assertThat(part.commandCount()).isEqualTo(50);
        assertThat(part.maxPoints()).isEqualByComparingTo("50");
        assertThat(part.objectiveCount()).isEqualTo(50);
        assertThat(part.essayCount()).isZero();
    }

    @Test
    void reportsSetsCommandsPointsAndOmittedEssay() {
        var document = codec.read("""
                {"parts":[{"id":"p","name":"Part","questionCount":8,"sections":[
                  {"id":"s","subjectCode":"VAN","questionCount":8,"blocks":[
                    {"id":"set","questionType":"QUESTION_SET","childAnswerType":"SINGLE_CHOICE",
                     "setCount":2,"setSize":3,"requiredPointsPerQuestion":0.75},
                    {"id":"tf","questionType":"TRUE_FALSE","questionCount":2,
                     "itemShape":{"statements":4},"requiredPointsPerQuestion":1},
                    {"id":"essay","questionType":"ESSAY","questionCount":1,"omitted":true,
                     "requiredPointsPerQuestion":4}]}]}]}
                """);
        var row = ExamCheckUnitSummary.summarize(new ExamStructureResponse(UUID.randomUUID(), document.parts(), 1))
                .parts().getFirst();
        assertThat(row.allocated()).isEqualTo(8);
        assertThat(row.commandCount()).isEqualTo(14);
        assertThat(row.maxPoints()).isEqualByComparingTo("6.5");
        assertThat(row.essayCount()).isZero();
        assertThat(row.objectiveCount()).isEqualTo(8);
        var set = row.children().getFirst().children().getFirst();
        assertThat(set.setCount()).isEqualTo(2);
        assertThat(set.childCount()).isEqualTo(6);
    }

    @Test
    void reportsFlexibleChildrenAndRetainsFixedSetMultiplication() {
        var document = codec.read("""
                {"parts":[{"id":"p","questionCount":30,"sections":[
                  {"id":"s","subjectCode":"TOAN","questionCount":30,"blocks":[
                    {"id":"single","questionType":"SINGLE_CHOICE","questionCount":12,"requiredPointsPerQuestion":10},
                    {"id":"flex","questionType":"QUESTION_SET","childAnswerType":"SINGLE_CHOICE",
                     "questionCount":18,"requiredPointsPerQuestion":10}]}]},
                  {"id":"fixed-part","sections":[{"id":"fixed-section","subjectCode":"TOAN","blocks":[
                    {"id":"fixed","questionType":"QUESTION_SET","childAnswerType":"SINGLE_CHOICE",
                     "questionCount":99,"setCount":2,"setSize":3,"requiredPointsPerQuestion":10}]}]}]}
                """);
        var parts = ExamCheckUnitSummary.summarize(new ExamStructureResponse(UUID.randomUUID(), document.parts(), 1)).parts();
        var part = parts.getFirst();
        var section = part.children().getFirst();
        var flex = section.children().get(1);
        assertThat(part.allocated()).isEqualTo(30);
        assertThat(section.allocated()).isEqualTo(30);
        assertThat(section.maxPoints()).isEqualByComparingTo("300");
        assertThat(flex.setCount()).isNull();
        assertThat(flex.childCount()).isEqualTo(18);
        assertThat(flex.allocated()).isEqualTo(18);
        assertThat(flex.maxPoints()).isEqualByComparingTo("180");
        var fixed = parts.get(1).children().getFirst().children().getFirst();
        assertThat(fixed.setCount()).isEqualTo(2);
        assertThat(fixed.childCount()).isEqualTo(6);
        assertThat(fixed.allocated()).isEqualTo(6);
        assertThat(fixed.maxPoints()).isEqualByComparingTo("60");
    }

    @Test
    void neverAddsMutuallyExclusiveCodeSubjects() {
        var document = codec.read("""
                {"parts":[{"id":"p","name":"Choice","questionCount":15,
                  "codeSource":{"source":"GLOBAL_LIST"},"subjectPool":[
                    {"id":"a","subjectCode":"A","questionCount":15,"blocks":[
                     {"id":"a1","questionType":"SINGLE_CHOICE","questionCount":15,"requiredPointsPerQuestion":1}]},
                    {"id":"b","subjectCode":"B","questionCount":15,"blocks":[
                     {"id":"b1","questionType":"SINGLE_CHOICE","questionCount":15,"requiredPointsPerQuestion":1}]}]}]}
                """);
        var combinations = List.of(
                new vn.unicom.thi360.exam.api.StructureCombination("p", "CA1", null, null, "BCA", List.of("A"), List.of()),
                new vn.unicom.thi360.exam.api.StructureCombination("p", "CA2", null, null, "BCA", List.of("B"), List.of()));
        var structure = new ExamStructureResponse(UUID.randomUUID(), document.parts(), 1, List.of(), combinations, null);
        var part = ExamCheckUnitSummary.summarize(structure).parts().getFirst();
        assertThat(part.allocated()).isEqualTo(15);
        assertThat(part.difference()).isZero();
        assertThat(part.children()).hasSize(2);
    }
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
}
