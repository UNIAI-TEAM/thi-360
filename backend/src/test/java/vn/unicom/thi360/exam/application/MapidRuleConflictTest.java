package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
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
 * UNI-2758 MAPID_RULE_CONFLICT: a static check on the compiled rules. Every conflict names the rules that cannot
 * hold together; the issue is an ERROR so the check, and with it the publish, fails.
 */
class MapidRuleConflictTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID PARENT = UUID.randomUUID();
    private static final UUID CHILD = UUID.randomUUID();

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void subjectExactBelowAGroupMinimumNamesBothRules() {
        var issues = conflicts(MapidMode.MATRIX,
                count("subject", null, MapidComparator.EXACT, 2, A),
                count("group", "g1", MapidComparator.AT_LEAST, 3, A));
        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(ruleIds(issue)).containsExactlyInAnyOrder("subject", "group");
            assertThat(issue.path("conflictKind").asText()).isEqualTo("SUBJECT_EXACT_BELOW_GROUP_MINIMUM");
            assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
            assertThat(issue.path("group").asText()).isEqualTo("MATRIX");
            assertThat(issue.path("sectionId").asText()).isEqualTo("s");
            assertThat(issue.path("blockId").asText()).isEqualTo("g1");
        });
    }

    @Test
    void descendantCountsTowardTheSubjectNode() {
        var covered = conflicts(MapidMode.MATRIX,
                count("subject", null, MapidComparator.EXACT, 2, PARENT),
                count("group", "g1", MapidComparator.AT_LEAST, 3, CHILD));
        assertThat(covered).singleElement().satisfies(issue ->
                assertThat(ruleIds(issue)).containsExactlyInAnyOrder("subject", "group"));
        // Exactly 2 of the child code leaves room for 3 questions of the wider parent code.
        var narrower = conflicts(MapidMode.MATRIX,
                count("subject", null, MapidComparator.EXACT, 2, CHILD),
                count("group", "g1", MapidComparator.AT_LEAST, 3, PARENT));
        assertThat(narrower).isEmpty();
    }

    @Test
    void groupMinimumAboveTheGroupUnitsIsFlaggedOnThatRule() {
        var exact = conflicts(MapidMode.MATRIX, count("set", "g2", MapidComparator.EXACT, 4, A));
        assertThat(exact).singleElement().satisfies(issue -> {
            assertThat(ruleIds(issue)).containsExactly("set");
            assertThat(issue.path("conflictKind").asText()).isEqualTo("GROUP_MINIMUM_ABOVE_GROUP_UNITS");
            assertThat(issue.path("blockId").asText()).isEqualTo("g2");
            assertThat(issue.path("required").asInt()).isEqualTo(4);
            assertThat(issue.path("available").asInt()).isEqualTo(3);
        });
        assertThat(conflicts(MapidMode.MATRIX, count("short", "g1", MapidComparator.AT_LEAST, 16, A))).hasSize(1);
        assertThat(conflicts(MapidMode.MATRIX, count("short", "g1", MapidComparator.AT_LEAST, 15, A))).isEmpty();
        assertThat(conflicts(MapidMode.MATRIX, new MapidRule("pct", MapidRuleType.COUNT, "g1", List.of(A),
                MapidComparator.AT_LEAST, null, new BigDecimal("100")))).isEmpty();
        var onlyWithCount = conflicts(MapidMode.MATRIX, new MapidRule("only", MapidRuleType.ONLY_IN_GROUP, "g2",
                List.of(A), MapidComparator.AT_LEAST, 4, null));
        assertThat(onlyWithCount).singleElement().satisfies(issue -> assertThat(ruleIds(issue)).containsExactly("only"));
    }

    @Test
    void twoExactRulesOnTheSameNodeNeedTheSameNumber() {
        var subject = conflicts(MapidMode.MATRIX,
                count("two", null, MapidComparator.EXACT, 2, A),
                count("three", null, MapidComparator.EXACT, 3, A));
        assertThat(subject).singleElement().satisfies(issue -> {
            assertThat(ruleIds(issue)).containsExactlyInAnyOrder("two", "three");
            assertThat(issue.path("conflictKind").asText()).isEqualTo("EXACT_COUNTS_DIFFER");
        });
        var group = conflicts(MapidMode.MATRIX,
                count("two", "g1", MapidComparator.EXACT, 2, A),
                count("three", "g1", MapidComparator.EXACT, 3, A));
        assertThat(group).hasSize(1);
        assertThat(conflicts(MapidMode.MATRIX,
                count("two", "g1", MapidComparator.EXACT, 2, A),
                count("three", "g3", MapidComparator.EXACT, 3, A))).isEmpty();
        assertThat(conflicts(MapidMode.MATRIX,
                count("a", null, MapidComparator.EXACT, 2, A),
                count("b", null, MapidComparator.EXACT, 2, A))).isEmpty();
        assertThat(conflicts(MapidMode.MATRIX,
                count("a", null, MapidComparator.EXACT, 2, A),
                count("b", null, MapidComparator.EXACT, 3, B))).isEmpty();
    }

    @Test
    void onlyInGroupAgainstAnotherGroupsCountOrPresenceOfTheNode() {
        var only = new MapidRule("only", MapidRuleType.ONLY_IN_GROUP, "g1", List.of(A), null, null, null);
        var other = conflicts(MapidMode.MATRIX, only,
                new MapidRule("present", MapidRuleType.PRESENT, "g3", List.of(A), null, null, null));
        assertThat(other).singleElement().satisfies(issue -> {
            assertThat(ruleIds(issue)).containsExactlyInAnyOrder("only", "present");
            assertThat(issue.path("conflictKind").asText()).isEqualTo("ONLY_IN_GROUP_EXCLUDES_OTHER_GROUP_RULE");
            assertThat(issue.path("blockId").asText()).isEqualTo("g3");
        });
        var counted = conflicts(MapidMode.MATRIX, only, count("counted", "g3", MapidComparator.AT_LEAST, 1, A));
        assertThat(counted).hasSize(1);
        // The target group itself, the whole subject, another node and a zero minimum are all fine.
        assertThat(conflicts(MapidMode.MATRIX, only,
                new MapidRule("same", MapidRuleType.PRESENT, "g1", List.of(A), null, null, null))).isEmpty();
        assertThat(conflicts(MapidMode.MATRIX, only,
                new MapidRule("subject", MapidRuleType.PRESENT, null, List.of(A), null, null, null))).isEmpty();
        assertThat(conflicts(MapidMode.MATRIX, only,
                new MapidRule("other", MapidRuleType.PRESENT, "g3", List.of(B), null, null, null))).isEmpty();
        assertThat(conflicts(MapidMode.MATRIX, only, count("none", "g3", MapidComparator.EXACT, 0, A))).isEmpty();
    }

    @Test
    void groupMinimumsThatAddUpAboveTheSubjectExactNameEveryRule() {
        var over = conflicts(MapidMode.MATRIX,
                count("subject", null, MapidComparator.EXACT, 4, A),
                count("first", "g1", MapidComparator.AT_LEAST, 3, A),
                count("second", "g3", MapidComparator.AT_LEAST, 2, A));
        assertThat(over).anySatisfy(issue -> {
            assertThat(issue.path("conflictKind").asText()).isEqualTo("GROUP_MINIMUMS_ABOVE_SUBJECT_EXACT");
            assertThat(ruleIds(issue)).containsExactlyInAnyOrder("subject", "first", "second");
        });
        var fits = conflicts(MapidMode.MATRIX,
                count("subject", null, MapidComparator.EXACT, 4, A),
                count("first", "g1", MapidComparator.AT_LEAST, 2, A),
                count("second", "g3", MapidComparator.AT_LEAST, 2, A));
        assertThat(fits).isEmpty();
    }

    @Test
    void conflictsOfOneSectionKeepDistinctKeys() {
        var issues = conflicts(MapidMode.MATRIX,
                count("subject", null, MapidComparator.EXACT, 1, A),
                count("first", "g1", MapidComparator.AT_LEAST, 2, A),
                count("second", "g3", MapidComparator.AT_LEAST, 2, A));
        assertThat(issues).hasSizeGreaterThanOrEqualTo(2);
        assertThat(issues.stream().map(issue -> issue.path("key").asText()).distinct()).hasSameSizeAs(issues);
    }

    @Test
    void rulesAreOnlyCheckedWhenTheyCompile() {
        var rules = new MapidRule[] {
                count("subject", null, MapidComparator.EXACT, 2, A),
                count("group", "g1", MapidComparator.AT_LEAST, 3, A)};
        assertThat(conflicts(MapidMode.ALL, rules)).isEmpty();
        assertThat(conflicts(null, rules)).isEmpty();
    }

    private List<ObjectNode> conflicts(MapidMode mode, MapidRule... rules) {
        var blocks = List.of(block("g1", QuestionType.SHORT_ANSWER, 15, null, null),
                block("g2", QuestionType.QUESTION_SET, null, 1, 3),
                block("g3", QuestionType.SINGLE_CHOICE, 32, null, null));
        var section = new ExamSection("s", "MATH", 50, null, null, null, null, blocks, List.of(), null, mode,
                List.of(rules));
        var part = new ExamPart("p", 1, "Part", 20, 50, 50, 50, new PartParticipation(ParticipationMode.ALL, null),
                null, List.of(section), List.of());
        var exam = new Exam(UUID.randomUUID(), "EXAM", "Exam", null, "FULL", "SHARED", 60, null, "RAW",
                Instant.parse("2026-11-20T00:00:00Z"), UUID.randomUUID());
        exam.applyStructureSummary(50, false, false, 0);
        var rulesCheck = new ExamCheckRules(mapper, exam, new ExamStructureDocument(List.of(part)), Map.of(),
                Map.of(CHILD, PARENT));
        List<ObjectNode> found = new ArrayList<>();
        for (ObjectNode issue : rulesCheck.unrepresentable()) {
            if ("MAPID_RULE_CONFLICT".equals(issue.path("code").asText())) found.add(issue);
        }
        return found;
    }

    private static List<String> ruleIds(JsonNode issue) {
        List<String> ids = new ArrayList<>();
        issue.path("ruleIds").forEach(id -> ids.add(id.asText()));
        return ids;
    }

    private static MapidRule count(String id, String groupId, MapidComparator comparator, int count, UUID... nodes) {
        return new MapidRule(id, MapidRuleType.COUNT, groupId, List.of(nodes), comparator, count, null);
    }

    private static QuestionBlock block(String id, QuestionType type, Integer count, Integer setCount, Integer setSize) {
        return new QuestionBlock(id, id, type, count, setCount, setSize, null, null, null, null, false, null,
                List.of(), null, true, false, List.of(), List.of(), null, List.of(), false, List.of(), null);
    }
}
