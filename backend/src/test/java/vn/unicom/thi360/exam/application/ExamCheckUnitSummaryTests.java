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
}
