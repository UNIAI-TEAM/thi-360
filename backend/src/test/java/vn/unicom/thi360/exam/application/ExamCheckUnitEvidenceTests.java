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
        var facts = ExamCheckUnitEvidence.witnessFacts(source, org.mockito.Mockito.mock(vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.class), document, List.of(path), Map.of()).get(0L);
        assertThat(facts).hasSize(2);
        assertThat(facts.getFirst().questionCode()).isEqualTo("CHILD");
        assertThat(facts.getFirst().hasAnswerKey()).isFalse();
        assertThat(facts.getFirst().location().slotIndex()).isEqualTo(4);
        assertThat(facts.getFirst().location().childPosition()).isEqualTo(1);
        assertThat(facts.getLast().hasAnswerKey()).isTrue();
    }
    @Test
    void essayRequiresManualGradingGuideButNoAutomaticAnswer() throws Exception {
        var mapper = new ObjectMapper();
        var document = new ExamStructureCodec(mapper).read("""
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"VAN","questionCount":2,
                  "blocks":[{"id":"b","questionType":"ESSAY","questionCount":2}]}]}]}
                """);
        UUID good = UUID.randomUUID(), bad = UUID.randomUUID();
        var goodKey = new QuestionSourcePort.VersionKey(good, 1);
        var badKey = new QuestionSourcePort.VersionKey(bad, 1);
        var source = mock(QuestionSourcePort.class);
        when(source.loadVersions(anyList())).thenReturn(List.of(
                new QuestionSourcePort.Version(goodKey, mapper.readTree(
                        "{\"body\":{\"type\":\"ESSAY\",\"gradingGuide\":\"<p>Guide</p>\"}}"), "GOOD", Map.of()),
                new QuestionSourcePort.Version(badKey, mapper.readTree(
                        "{\"body\":{\"type\":\"ESSAY\",\"gradingGuide\":\"\"}}"), "BAD", Map.of())));
        var witness = new SubjectWitness("VAN", 2, List.of(new GroupWitness("b",
                List.of(new QuestionPick(good.toString(), 1), new QuestionPick(bad.toString(), 1)))));
        var path = new PathResult(SolvePath.of(0, List.of()), SolveStatus.VALID, null, null, null,
                List.of(witness), List.of(), null);
        var facts = ExamCheckUnitEvidence.witnessFacts(source, org.mockito.Mockito.mock(vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.class), document, List.of(path), Map.of()).get(0L);
        assertThat(facts).hasSize(2);
        assertThat(facts.get(0).hasAnswerKey()).isNull();
        assertThat(facts.get(0).hasScoringRule()).isTrue();
        assertThat(facts.get(1).hasScoringRule()).isFalse();
    }

    /** FU-BE9-2: a True/False question of an SP2E block scored per statement must carry 0.2 a statement; no other block is asked. */
    @Test
    void unevenStatementPointsAreReportedOnlyInAPerStatementSp2eBlock() throws Exception {
        var mapper = new ObjectMapper();
        String structure = """
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"LY","questionCount":1,"blocks":[
                  {"id":"per","questionType":"TRUE_FALSE","questionCount":1,"expectedScoring":{"trueFalseMethod":"PER_STATEMENT"}},
                  {"id":"table","questionType":"TRUE_FALSE","questionCount":1,"expectedScoring":{"trueFalseMethod":"COUNT_TABLE"}}]}]}]}
                """;
        var document = new ExamStructureCodec(mapper).read(structure);
        UUID even = UUID.randomUUID(), uneven = UUID.randomUUID();
        String evenBody = "{\"body\":{\"type\":\"TRUE_FALSE\",\"scoringMethod\":\"PER_STATEMENT\",\"statements\":["
                + "{\"answer\":true,\"points\":0.2},{\"answer\":false,\"points\":0.20},{\"answer\":true,\"points\":0.2},"
                + "{\"answer\":false,\"points\":0.2}]}}";
        String unevenBody = "{\"body\":{\"type\":\"TRUE_FALSE\",\"scoringMethod\":\"PER_STATEMENT\",\"statements\":["
                + "{\"answer\":true,\"points\":0.1},{\"answer\":false,\"points\":0.3},{\"answer\":true,\"points\":0.2},"
                + "{\"answer\":false,\"points\":0.2}]}}";
        var source = mock(QuestionSourcePort.class);
        when(source.loadVersions(anyList())).thenReturn(List.of(
                new QuestionSourcePort.Version(new QuestionSourcePort.VersionKey(even, 1), mapper.readTree(evenBody), "EVEN", Map.of()),
                new QuestionSourcePort.Version(new QuestionSourcePort.VersionKey(uneven, 1), mapper.readTree(unevenBody), "UNEVEN", Map.of())));
        var witness = new SubjectWitness("LY", 3, List.of(
                new GroupWitness("per", List.of(new QuestionPick(even.toString(), 1), new QuestionPick(uneven.toString(), 1))),
                new GroupWitness("table", List.of(new QuestionPick(uneven.toString(), 1)))));
        var path = new PathResult(SolvePath.of(0, List.of()), SolveStatus.VALID, null, null, null,
                List.of(witness), List.of(), null);
        var attachments = mock(vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.class);

        var sp2e = ExamCheckUnitEvidence.ownedWitnessFacts(source, attachments, document, List.of(path), Map.of(), "SP2E")
                .get(0L).stream().map(ExamCheckUnitEvidence.OwnedFact::fact).toList();
        assertThat(sp2e).extracting(fact -> fact.location().blockId() + ":" + fact.questionCode() + ":" + fact.statementPointsEven())
                .containsExactly("per:EVEN:true", "per:UNEVEN:false", "table:UNEVEN:null");
        var rules = new ExamCheckRules(mapper, new vn.unicom.thi360.exam.domain.Exam(UUID.randomUUID(), "E", "E", null,
                "FULL", "SHARED", 60, null, "RAW", java.time.Instant.now(), UUID.randomUUID()), document);
        var issues = rules.witnessIssues(sp2e);
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().path("code").asText()).isEqualTo("STATEMENT_POINTS_UNEVEN");
        assertThat(issues.getFirst().path("severity").asText()).isEqualTo("ERROR");
        assertThat(issues.getFirst().path("blockId").asText()).isEqualTo("per");

        var thptqg = ExamCheckUnitEvidence.ownedWitnessFacts(source, attachments, document, List.of(path), Map.of(), "THPTQG")
                .get(0L).stream().map(ExamCheckUnitEvidence.OwnedFact::fact).toList();
        assertThat(thptqg).extracting(ExamCheckRules.WitnessFact::statementPointsEven).containsOnlyNulls();
    }
}
