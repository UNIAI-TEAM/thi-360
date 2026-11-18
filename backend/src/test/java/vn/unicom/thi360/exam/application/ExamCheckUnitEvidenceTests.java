package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SubjectWitness;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

class ExamCheckUnitEvidenceTests {
    @Test
    void reportsInvalidChildFromActualWitnessSnapshot() throws Exception {
        var mapper = new ObjectMapper();
        var document = new ExamStructureCodec(mapper).read("""
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"VAN","questionCount":2,
                  "blocks":[{"id":"set","questionType":"QUESTION_SET","setCount":1,"setSize":2}]}]}]}
                """);
        UUID root = UUID.randomUUID(), child = UUID.randomUUID();
        var key = new QuestionSourcePort.VersionKey(root, 3);
        var snapshot = mapper.readTree("""
                {"attachmentIds":[],"body":{"type":"QUESTION_SET","children":[
                  {"id":"%s","body":{"type":"SHORT_ANSWER","answers":[]}},
                  {"body":{"type":"SINGLE_CHOICE","options":[{"correct":true},{"correct":false}]}}]}}
                """.formatted(child));
        var source = mock(QuestionSourcePort.class);
        when(source.loadVersions(anyList())).thenReturn(List.of(new QuestionSourcePort.Version(key, snapshot,
                "ROOT", Map.of(child, "CHILD"))));
        var witness = new SubjectWitness("VAN", 2, List.of(new GroupWitness("set:slot:4",
                List.of(new QuestionPick(root.toString(), 3)))));
        var path = new PathResult(SolvePath.of(0, List.of()), SolveStatus.VALID, null, null, null,
                List.of(witness), List.of(), null);
        var facts = ExamCheckUnitEvidence.witnessFacts(source, document, List.of(path), Map.of()).get(0L);
        assertThat(facts).hasSize(2);
        assertThat(facts.getFirst().questionCode()).isEqualTo("CHILD");
        assertThat(facts.getFirst().hasAnswerKey()).isFalse();
        assertThat(facts.getFirst().location().slotIndex()).isEqualTo(4);
        assertThat(facts.getFirst().location().childPosition()).isEqualTo(1);
        assertThat(facts.getLast().hasAnswerKey()).isTrue();
    }
}
