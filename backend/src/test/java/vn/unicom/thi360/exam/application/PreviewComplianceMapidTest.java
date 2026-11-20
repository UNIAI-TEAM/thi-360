package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.exam.api.ExamPreviewController.Compliance;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** UNI-2758: the preview shows one compliance row per MAPID rule, required against what the paper holds. */
class PreviewComplianceMapidTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID A_CHILD = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private final ExamStructureCodec codec = new ExamStructureCodec(new ObjectMapper());

    /** Section of 4 questions: g1 q1 (A) q2 (-), g2 q3 (child of A) q4 (B). */
    private List<Compliance> rows(String mode, String rules) {
        var document = codec.read("""
                {"parts":[{"id":"part","sections":[{"id":"math","subjectCode":"MATH","questionCount":4,
                  "mapidMode":%s,"mapidRules":%s,
                  "topicQuotas":[{"id":"legacy","nodeIds":["%s"],"count":1}],
                  "blocks":[{"id":"g1","questionType":"SINGLE_CHOICE","questionCount":2},
                            {"id":"g2","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """.formatted(mode, rules, A));
        UUID q1 = UUID.randomUUID(), q2 = UUID.randomUUID(), q3 = UUID.randomUUID(), q4 = UUID.randomUUID();
        var section = new SectionOnPath("MATH", "math", 4, null,
                List.of(GroupQuota.single("g1", 2), GroupQuota.single("g2", 2)));
        return PreviewCompliance.evaluate(document, SolvePath.of(0, List.of(section)),
                Map.of("g1", new GroupWitness("g1", List.of(pick(q1), pick(q2))),
                        "g2", new GroupWitness("g2", List.of(pick(q3), pick(q4)))),
                Map.of(key(q1), candidate(q1, A), key(q2), candidate(q2),
                        key(q3), candidate(q3, A_CHILD), key(q4), candidate(q4, B)),
                List.of(), List.of(item(A, "/a/"), item(A_CHILD, "/a/child/"), item(B, "/b/")));
    }

    @Test
    void oneRowPerRuleWithRequiredAndActual() {
        var rows = rows("\"MATRIX\"", """
                [{"id":"present","type":"PRESENT","nodeIds":["%1$s"]},
                 {"id":"exact","type":"COUNT","groupId":"g1","comparator":"EXACT","count":1,"nodeIds":["%1$s"]},
                 {"id":"atLeast","type":"COUNT","comparator":"AT_LEAST","count":2,"nodeIds":["%1$s"]},
                 {"id":"only","type":"ONLY_IN_GROUP","groupId":"g1","nodeIds":["%1$s"]}]
                """.formatted(A));
        // The child of A counts as A, in the subject and in the group.
        assertThat(rows).contains(
                new Compliance("MAPID:present", "SECTION:math", BigDecimal.ONE, BigDecimal.valueOf(2),
                        "REQUIRED", "QUESTION", null),
                new Compliance("MAPID:exact", "BLOCK:g1", BigDecimal.ONE, BigDecimal.ONE,
                        "REQUIRED", "QUESTION", null),
                new Compliance("MAPID:atLeast", "SECTION:math", BigDecimal.valueOf(2), BigDecimal.valueOf(2),
                        "REQUIRED", "QUESTION", null),
                // ONLY_IN_GROUP: nothing may carry the node outside the group; q3 does.
                new Compliance("MAPID:only", "SECTION:math", BigDecimal.ZERO, BigDecimal.ONE,
                        "REQUIRED", "QUESTION", null));
        assertThat(rows).extracting(Compliance::criterion).filteredOn(c -> c.startsWith("MAPID:")).hasSize(4);
    }

    @Test
    void anExactZeroAndAPercentAreResolvedLikeTheCompiler() {
        var rows = rows("\"MATRIX\"", """
                [{"id":"none","type":"COUNT","comparator":"EXACT","count":0,"nodeIds":["%1$s"],"groupId":"g2"},
                 {"id":"half","type":"COUNT","comparator":"AT_LEAST","percent":50,"nodeIds":["%2$s"]}]
                """.formatted(B, A));
        assertThat(rows).contains(
                // q4 carries B in g2: an exact 0 shows its violation as the actual.
                new Compliance("MAPID:none", "BLOCK:g2", BigDecimal.ZERO, BigDecimal.ONE, "REQUIRED", "QUESTION", null),
                new Compliance("MAPID:half", "SECTION:math", BigDecimal.valueOf(2), BigDecimal.valueOf(2),
                        "REQUIRED", "QUESTION", null));
    }

    @Test
    void anOnlyInGroupRuleWithACountAddsItsCountRowOnTheGroup() {
        var rows = rows("\"MATRIX\"", """
                [{"id":"only","type":"ONLY_IN_GROUP","groupId":"g1","comparator":"EXACT","count":1,"nodeIds":["%s"]}]
                """.formatted(A));
        assertThat(rows).contains(
                new Compliance("MAPID:only", "SECTION:math", BigDecimal.ZERO, BigDecimal.ONE, "REQUIRED", "QUESTION", null),
                new Compliance("MAPID:only:COUNT", "BLOCK:g1", BigDecimal.ONE, BigDecimal.ONE,
                        "REQUIRED", "QUESTION", null));
    }

    @Test
    void theLegacyTopicRowsOfASectionWithAModeAreNotReported() {
        for (String mode : List.of("\"MATRIX\"", "\"ALL\"")) {
            var rows = rows(mode, "[{\"id\":\"present\",\"type\":\"PRESENT\",\"nodeIds\":[\"" + A + "\"]}]");
            assertThat(rows).extracting(Compliance::criterion).noneMatch(criterion -> criterion.startsWith("TOPIC:"));
        }
    }

    @Test
    void allModeKeepsTheRulesInTheDraftAndReportsNone() {
        var rows = rows("\"ALL\"", "[{\"id\":\"present\",\"type\":\"PRESENT\",\"nodeIds\":[\"" + A + "\"]}]");
        assertThat(rows).extracting(Compliance::criterion).noneMatch(criterion -> criterion.startsWith("MAPID:"));
    }

    private static QuestionPick pick(UUID id) {
        return new QuestionPick(id.toString(), 1);
    }

    private static QuestionSourcePort.VersionKey key(UUID id) {
        return new QuestionSourcePort.VersionKey(id, 1);
    }

    private static CatalogItemView item(UUID id, String path) {
        CatalogItemView item = mock(CatalogItemView.class);
        when(item.id()).thenReturn(id);
        when(item.path()).thenReturn(path);
        return item;
    }

    private static QuestionSourcePort.Candidate candidate(UUID id, UUID... nodes) {
        var unit = new QuestionSourcePort.Unit(id, id.toString(), QuestionType.SINGLE_CHOICE, "G12", "EASY",
                List.of(nodes), List.of(), BigDecimal.ONE);
        return new QuestionSourcePort.Candidate(id, 1, null, List.of(unit), null, null);
    }
}
