package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ExamVersionSnapshotTest {
    @Test
    void choiceCountsResolveEachPossibleSubjectQuotaWithoutChangingTheStructure() throws Exception {
        var mapper = new ObjectMapper();
        var structure = mapper.readTree("""
                {"parts":[{"options":[{"countsByPick":[17,17,16],"sections":[{"id":"history",
                "subjectCode":"HISTORY", "questionCount":null,
                "knowledgeScope":{"mode":"ANY","ids":["history-node"]},
                "answerQuotas":{"unit":"PERCENT","items":[{"answerType":"SINGLE_CHOICE","value":60},
                {"answerType":"SHORT_ANSWER","value":40}]},
                "blocks":[{"id":"remaining","questionCount":null}]}]}]}]}
                """);
        var snapshot = new ExamVersionSnapshot(mapper).resolvedQuotas(structure);
        assertThat(snapshot.at("/history/16/answerQuotas/0/value").intValue()).isEqualTo(10);
        assertThat(snapshot.at("/history/16/answerQuotas/1/value").intValue()).isEqualTo(6);
        assertThat(snapshot.at("/history/17/answerQuotas/0/value").intValue()).isEqualTo(10);
        assertThat(snapshot.at("/history/17/answerQuotas/1/value").intValue()).isEqualTo(7);
        assertThat(structure.at("/parts/0/options/0/countsByPick/0").intValue()).isEqualTo(17);
        assertThat(structure.at("/parts/0/options/0/sections/0/knowledgeScope/ids/0").asText()).isEqualTo("history-node");
    }
}
