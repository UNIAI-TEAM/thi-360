package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExamVersionSnapshotTest {
    @Test
    void choiceCountsResolveEachPossibleSubjectQuotaWithoutChangingTheStructure() throws Exception {
        var mapper = new ObjectMapper();
        var structure = mapper.readTree("""
                {"parts":[{"options":[{"id":"choice","countsByPick":[17,17,16],"sections":[{"id":"history",
                "subjectCode":"HISTORY", "questionCount":null,
                "knowledgeScope":{"mode":"ANY","ids":["history-node"]},
                "answerQuotas":{"unit":"PERCENT","items":[{"answerType":"SINGLE_CHOICE","value":60},
                {"answerType":"SHORT_ANSWER","value":40}]},
                "blocks":[{"id":"remaining","questionCount":null}]}]}]}]}
                """);
        var snapshot = new ExamVersionSnapshot(mapper).resolvedQuotas(structure, Set.of("choice"));
        assertThat(snapshot.at("/history/16/answerQuotas/0/value").intValue()).isEqualTo(10);
        assertThat(snapshot.at("/history/16/answerQuotas/1/value").intValue()).isEqualTo(6);
        assertThat(snapshot.at("/history/17/answerQuotas/0/value").intValue()).isEqualTo(10);
        assertThat(snapshot.at("/history/17/answerQuotas/1/value").intValue()).isEqualTo(7);
        assertThat(structure.at("/parts/0/options/0/countsByPick/0").intValue()).isEqualTo(17);
        assertThat(structure.at("/parts/0/options/0/sections/0/knowledgeScope/ids/0").asText()).isEqualTo("history-node");
    }

    @Test
    void resolvesSelectedOptionBlocksAndLeavesInvalidUnselectedQuotaUntouched() throws Exception {
        var mapper = new ObjectMapper();
        var structure = mapper.readTree("""
                {"parts":[{"options":[
                  {"id":"good","blocks":[{"id":"b","questionCount":2,"matrix":{"unit":"PERCENT",
                    "byGrade":[{"grade":"A","value":50},{"grade":"B","value":50}]}}]},
                  {"id":"bad","sections":[{"id":"bad-section","questionCount":1,
                    "answerQuotas":{"unit":"COUNT","items":[{"value":2147483648}]}}]}]}]}
                """);
        var snapshot = new ExamVersionSnapshot(mapper).resolvedQuotas(structure, Set.of("good"));
        assertThat(snapshot.at("/good:blocks/2/blocks/b/byGrade/0/value").intValue()).isEqualTo(1);
        assertThat(snapshot.at("/good:blocks/2/blocks/b/byGrade/1/value").intValue()).isEqualTo(1);
        assertThat(snapshot.has("bad-section")).isFalse();
        assertThat(structure.at("/parts/0/options/0/blocks/0/matrix/byGrade/0/value").intValue()).isEqualTo(50);
        assertThat(structure.at("/parts/0/options/1/sections/0/answerQuotas/items/0/value").longValue())
                .isEqualTo(2147483648L);
    }
}
