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

    @Test
    void partTotalsUseSelectedSubjectChoiceAndTrimOneQuestion() throws Exception {
        var mapper = new ObjectMapper();
        var structure = mapper.readTree("""
                {"parts":[{"id":"fixed","durationMinutes":10,"sections":[{"id":"a"}]},
                  {"id":"choice-part","durationMinutes":20,"options":[{"id":"choice","countsByPick":[17,17,16],
                    "sections":[{"id":"s1"},{"id":"s2"},{"id":"s3"}]}]}]}
                """);
        var check = mapper.readTree("{\"paths\":[{\"pathId\":\"choice\"}]}");
        var witness = mapper.readTree("""
                [{"path":{"chosen":[{"optionId":"choice"}],
                  "sections":[{"sectionId":"a","questionCount":1,"groups":[
                    {"kind":"SINGLE","questionCount":1,"requiredPointsPerUnit":2}]}],
                  "picks":[[{"optionId":"choice","counts":[17,17,17],"sections":[
                    {"sectionId":"s1","choiceGroup":"NATURAL","groups":[{"kind":"SINGLE","questionCount":null,"requiredPointsPerUnit":1}]},
                    {"sectionId":"s2","choiceGroup":"NATURAL","groups":[{"kind":"SINGLE","questionCount":null,"requiredPointsPerUnit":1}]},
                    {"sectionId":"s3","choiceGroup":"SOCIAL","groups":[{"kind":"SINGLE","questionCount":null,"requiredPointsPerUnit":1}]}
                  ]}]]}}]
                """);
        var result = new ExamVersionSnapshot(mapper).parts(structure, check, witness, Set.of("choice"), "PER_PART", null);
        assertThat(result.at("/parts/0/paths/0/questionCount").asInt()).isEqualTo(1);
        assertThat(result.at("/parts/0/paths/0/maxPoints").decimalValue()).isEqualByComparingTo("2");
        assertThat(result.at("/parts/0/paths/0/durationMinutes").asInt()).isEqualTo(10);
        assertThat(result.at("/parts/1/paths/0/questionCount").asInt()).isEqualTo(50);
        assertThat(result.at("/parts/1/paths/0/maxPoints").decimalValue()).isEqualByComparingTo("50");
        assertThat(result.at("/parts/1/paths/0/durationMinutes").asInt()).isEqualTo(20);
    }
}
