package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.api.ExamPreviewController.Compliance;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class PreviewComplianceTest {
    private final ExamStructureCodec codec = new ExamStructureCodec(new ObjectMapper());

    @Test
    void referenceGradeUsesFullQuotaLessDroppedContribution() {
        var document = codec.read("""
                {"parts":[{"id":"part","sections":[{"id":"english","subjectCode":"EN","questionCount":2,
                  "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REFERENCE",
                    "byGrade":[{"code":"G11","value":1},{"code":"G12","value":1}]},
                  "blocks":[{"id":"group","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """);
        UUID kept = UUID.randomUUID();
        UUID removed = UUID.randomUUID();
        var keepCandidate = candidate(kept, "G11", List.of());
        var dropCandidate = candidate(removed, "G12", List.of());
        var pick = new QuestionPick(kept.toString(), 1);
        var drop = new PreviewSnapshotStore.Dropped(new QuestionPick(removed.toString(), 1), "EN");
        var section = new SectionOnPath("EN", "english", 1, null, List.of(GroupQuota.single("group", 2)));

        List<Compliance> rows = PreviewCompliance.evaluate(document, SolvePath.of(0, List.of(section)),
                Map.of("group", new GroupWitness("group", List.of(pick))),
                Map.of(new QuestionSourcePort.VersionKey(kept, 1), keepCandidate,
                        new QuestionSourcePort.VersionKey(removed, 1), dropCandidate), List.of(drop), List.of());
        assertThat(rows).contains(
                new Compliance("GRADE:G11", "SECTION:english", BigDecimal.ONE, BigDecimal.ONE,
                        "REFERENCE", "QUESTION", "dropped 1"),
                new Compliance("GRADE:G12", "SECTION:english", BigDecimal.ZERO, BigDecimal.ZERO,
                        "REFERENCE", "QUESTION", "dropped 1"));
    }

    @Test
    void overlappingTopicQuotasDoNotCountOneQuestionTwice() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var document = codec.read("""
                {"parts":[{"id":"part","sections":[{"id":"math","subjectCode":"MATH","questionCount":2,
                  "topicQuotas":[{"id":"a","nodeIds":["%s"],"count":1},
                                 {"id":"b","nodeIds":["%s"],"count":1}],
                  "blocks":[{"id":"group","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """.formatted(a, b));
        UUID both = UUID.randomUUID();
        UUID onlyA = UUID.randomUUID();
        var picks = List.of(new QuestionPick(both.toString(), 1), new QuestionPick(onlyA.toString(), 1));
        var section = new SectionOnPath("MATH", "math", 2, null, List.of(GroupQuota.single("group", 2)));
        List<Compliance> rows = PreviewCompliance.evaluate(document, SolvePath.of(0, List.of(section)),
                Map.of("group", new GroupWitness("group", picks)),
                Map.of(new QuestionSourcePort.VersionKey(both, 1), candidate(both, "G12", List.of(a, b)),
                        new QuestionSourcePort.VersionKey(onlyA, 1), candidate(onlyA, "G12", List.of(a))),
                List.of(), List.of());
        assertThat(rows).contains(
                new Compliance("TOPIC:a", "SECTION:math", BigDecimal.ONE, BigDecimal.ONE, "REQUIRED", "QUESTION", null),
                new Compliance("TOPIC:b", "SECTION:math", BigDecimal.ONE, BigDecimal.ONE, "REQUIRED", "QUESTION", null));
    }

    private static QuestionSourcePort.Candidate candidate(UUID id, String grade, List<UUID> nodes) {
        var unit = new QuestionSourcePort.Unit(id, id.toString(), QuestionType.SINGLE_CHOICE, grade,
                "EASY", nodes, List.of(), BigDecimal.ONE);
        return new QuestionSourcePort.Candidate(id, 1, null, List.of(unit), null, null);
    }
}
