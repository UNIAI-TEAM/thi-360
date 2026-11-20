package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MapidComparator;
import vn.unicom.thi360.exam.domain.MapidMode;
import vn.unicom.thi360.exam.domain.MapidRule;
import vn.unicom.thi360.exam.domain.MapidRuleType;
import vn.unicom.thi360.exam.domain.PartParticipation;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * UNI-2758 BE-3b: a saved rule whose group no longer exists is an ERROR of the check (it blocks publish) that names
 * the rule; it never makes the check throw, and the other rules keep working.
 */
class MapidRuleGroupMissingTest {
    private static final UUID A = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everyRuleOfAMissingGroupIsReportedWithItsRuleId() {
        var issues = missing(MapidMode.MATRIX,
                new MapidRule("present", MapidRuleType.PRESENT, "gone", List.of(A), null, null, null),
                new MapidRule("count", MapidRuleType.COUNT, "gone2", List.of(A), MapidComparator.EXACT, 1, null),
                new MapidRule("source", MapidRuleType.SOURCE, "gone", List.of(A), null, null, null),
                new MapidRule("fine", MapidRuleType.PRESENT, "g1", List.of(A), null, null, null),
                new MapidRule("subject", MapidRuleType.PRESENT, null, List.of(A), null, null, null));
        assertThat(issues).extracting(issue -> issue.path("ruleId").asText())
                .containsExactlyInAnyOrder("present", "count", "source");
        assertThat(issues).allSatisfy(issue -> {
            assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
            assertThat(issue.path("group").asText()).isEqualTo("MATRIX");
            assertThat(issue.path("sectionId").asText()).isEqualTo("s");
        });
        assertThat(issues.stream().map(issue -> issue.path("key").asText()).distinct()).hasSize(3);
    }

    @Test
    void theOtherChecksOfTheSectionDoNotThrowAndStayQuiet() {
        // Conflicts are computed on the rules that still compile; the missing one is not compared.
        var all = unrepresentable(MapidMode.MATRIX,
                new MapidRule("gone", MapidRuleType.COUNT, "gone", List.of(A), MapidComparator.AT_LEAST, 99, null));
        assertThat(all).extracting(issue -> issue.path("code").asText())
                .contains("MAPID_RULE_GROUP_MISSING").doesNotContain("MAPID_RULE_CONFLICT");
    }

    @Test
    void anAllSectionKeepsItsRulesInTheDraftWithoutAnError() {
        assertThat(missing(MapidMode.ALL,
                new MapidRule("present", MapidRuleType.PRESENT, "gone", List.of(A), null, null, null))).isEmpty();
        assertThat(missing(null,
                new MapidRule("present", MapidRuleType.PRESENT, "gone", List.of(A), null, null, null))).isEmpty();
    }

    private List<ObjectNode> missing(MapidMode mode, MapidRule... rules) {
        List<ObjectNode> found = new ArrayList<>();
        for (ObjectNode issue : unrepresentable(mode, rules)) {
            if ("MAPID_RULE_GROUP_MISSING".equals(issue.path("code").asText())) found.add(issue);
        }
        return found;
    }

    private List<ObjectNode> unrepresentable(MapidMode mode, MapidRule... rules) {
        var block = new QuestionBlock("g1", "g1", QuestionType.SINGLE_CHOICE, 10, null, null, null, null, null, null,
                false, null, List.of(), null, true, false, List.of(), List.of(), null, List.of(), false, List.of(), null);
        var section = new ExamSection("s", "MATH", 10, null, null, null, null, List.of(block), List.of(), null, mode,
                List.of(rules));
        var part = new ExamPart("p", 1, "Part", 20, 10, 10, 10, new PartParticipation(ParticipationMode.ALL, null),
                null, List.of(section), List.of());
        var exam = new Exam(UUID.randomUUID(), "EXAM", "Exam", null, "FULL", "SHARED", 60, null, "RAW",
                Instant.parse("2026-11-20T00:00:00Z"), UUID.randomUUID());
        exam.applyStructureSummary(10, false, false, 0);
        return new ExamCheckRules(mapper, exam, new ExamStructureDocument(List.of(part)), Map.of(), Map.of())
                .unrepresentable();
    }
}
