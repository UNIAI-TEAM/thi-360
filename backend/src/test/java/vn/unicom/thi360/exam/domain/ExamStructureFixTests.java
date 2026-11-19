package vn.unicom.thi360.exam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.shared.api.BusinessRuleException;

class ExamStructureFixTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExamStructureDocument document(String json) throws Exception {
        return MAPPER.readValue(json, ExamStructureDocument.class);
    }

    @Test
    void duplicationRemapsSectionAndBlockQuotaGroups() throws Exception {
        var original = document("""
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"MATH",
                  "blocks":[{"id":"b","topicQuotas":[{"id":"q1","count":1,"groupIds":["b"]}]}],
                  "topicQuotas":[{"id":"q2","count":1,"groupIds":["b"]}]}]}]}
                """);
        var copy = ExamStructureOps.regenerateIds(original);
        var section = copy.parts().getFirst().sections().getFirst();
        var block = section.blocks().getFirst();
        assertThat(block.id()).isNotEqualTo("b");
        assertThat(section.topicQuotas().getFirst().groupIds()).containsExactly(block.id());
        assertThat(block.topicQuotas().getFirst().groupIds()).containsExactly(block.id());
    }


    @Test
    void templateOwnedShuffleFlagSurvivesClientEdits() throws Exception {
        var stored = document("""
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"MATH",
                  "blocks":[{"id":"b","shuffleEditable":true,"shuffleUnits":true}]}]}]}
                """);
        var incoming = document("""
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"MATH",
                  "blocks":[{"id":"b","shuffleEditable":false,"shuffleUnits":false},
                            {"id":"new","shuffleEditable":true}]}]}]}
                """);
        var result = ExamStructureOps.restoreTemplateOwned(incoming, stored, stored)
                .parts().getFirst().sections().getFirst().blocks();
        assertThat(result.get(0).shuffleEditable()).isTrue();
        assertThat(result.get(0).shuffleUnits()).isFalse();
        assertThat(result.get(1).shuffleEditable()).isFalse();
    }
    @Test
    void sourceVocabularyAndNumericRangesAreCheckedWithoutRejectingPartialDrafts() throws Exception {
        for (String source : List.of("{\"source\":\"TYPO\"}",
                "{\"source\":\"GLOBAL_LIST\",\"branch\":\"TYPO\"}")) {
            var doc = document("{\"parts\":[{\"id\":\"p\",\"codeSource\":" + source + "}]}");
            assertThatThrownBy(() -> ExamStructureValidator.validate(doc)).isInstanceOf(BusinessRuleException.class);
        }
        for (String field : List.of("\"itemShape\":{\"statements\":-1}",
                "\"childConditions\":[{\"from\":0,\"to\":1}]",
                "\"childConditions\":[{\"from\":3,\"to\":2}]")) {
            var doc = document("{\"parts\":[{\"id\":\"p\",\"sections\":[{\"id\":\"s\",\"subjectCode\":\"MATH\",\"blocks\":[{\"id\":\"b\"," + field + "}]}]}]}");
            assertThatThrownBy(() -> ExamStructureValidator.validate(doc)).isInstanceOf(BusinessRuleException.class);
        }
        ExamStructureValidator.validate(document("{\"parts\":[{\"id\":\"p\",\"codeSource\":{\"source\":\"GLOBAL_LIST\"}}]}"));
    }

    @Test
    void subjectPoolCountsTowardsPartBlockLimit() throws Exception {
        for (int count : List.of(30, 31)) {
            String blocks = java.util.stream.IntStream.range(0, count)
                    .mapToObj(i -> "{\"id\":\"b" + i + "\"}").collect(java.util.stream.Collectors.joining(","));
            var doc = document("{\"parts\":[{\"id\":\"p\",\"codeSource\":{\"source\":\"GLOBAL_LIST\"},\"subjectPool\":[{\"id\":\"s\",\"subjectCode\":\"MATH\",\"blocks\":[" + blocks + "]}]}]}");
            if (count == 30) {
                ExamStructureValidator.validate(doc);
            } else {
                assertThatThrownBy(() -> ExamStructureValidator.validate(doc))
                        .isInstanceOf(BusinessRuleException.class)
                        .extracting(ex -> ((BusinessRuleException) ex).getCode())
                        .isEqualTo("EXAM_PATH_LIMIT_EXCEEDED");
            }
        }
    }
}
