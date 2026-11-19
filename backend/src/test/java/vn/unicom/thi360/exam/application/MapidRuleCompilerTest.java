package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.application.CompiledMapidRules.Cap;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.KnowledgeScope;
import vn.unicom.thi360.exam.domain.MapidComparator;
import vn.unicom.thi360.exam.domain.MapidMode;
import vn.unicom.thi360.exam.domain.MapidRule;
import vn.unicom.thi360.exam.domain.MapidRuleType;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.QuotaBasis;
import vn.unicom.thi360.exam.domain.QuotaEnforcement;
import vn.unicom.thi360.exam.domain.TopicQuota;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class MapidRuleCompilerTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID LEGACY = UUID.randomUUID();

    @Test
    void identityWithoutMode() {
        ExamSection section = section(null, List.of(rule("r", MapidRuleType.PRESENT, null, A)));
        var compiled = MapidRuleCompiler.compile(section);
        assertThat(compiled.effectiveSection()).isSameAs(section);
        assertThat(compiled.caps()).isEmpty();
        assertThat(compiled.exclusions()).isEmpty();
    }

    @Test
    void allModeClearsNodesAndQuotasKeepsCompetenciesAndIgnoresRules() {
        ExamSection section = section(MapidMode.ALL, List.of(
                rule("p", MapidRuleType.PRESENT, null, A),
                count("c", "g2", MapidComparator.EXACT, 2, null, B),
                new MapidRule("o", MapidRuleType.ONLY_IN_GROUP, "g2", List.of(C), null, null, null),
                rule("s", MapidRuleType.SOURCE, null, A)));
        var compiled = MapidRuleCompiler.compile(section);
        ExamSection effective = compiled.effectiveSection();
        assertThat(effective.knowledgeScope().nodeIds()).isEmpty();
        assertThat(effective.knowledgeScope().competencyCodes()).containsExactly("SEC");
        assertThat(effective.topicQuotas()).isEmpty();
        assertThat(effective.blocks()).allSatisfy(block -> assertThat(block.topicQuotas()).isEmpty());
        QuestionBlock g2 = block(effective, "g2");
        assertThat(g2.knowledgeScope().nodeIds()).isEmpty();
        assertThat(g2.knowledgeScope().competencyCodes()).containsExactly("BLK");
        assertThat(effective.mapidRules()).hasSize(4);
        assertThat(effective.mapidMode()).isEqualTo(MapidMode.ALL);
        assertThat(compiled.caps()).isEmpty();
        assertThat(compiled.exclusions()).isEmpty();
    }

    @Test
    void matrixIgnoresLegacyNodesAndQuotas() {
        var compiled = MapidRuleCompiler.compile(section(MapidMode.MATRIX, List.of()));
        ExamSection effective = compiled.effectiveSection();
        assertThat(effective.knowledgeScope().nodeIds()).isEmpty();
        assertThat(effective.knowledgeScope().everyChildInScope()).isNull();
        assertThat(effective.knowledgeScope().competencyCodes()).containsExactly("SEC");
        assertThat(effective.topicQuotas()).isEmpty();
        assertThat(block(effective, "g2").knowledgeScope().nodeIds()).isEmpty();
        assertThat(block(effective, "g2").topicQuotas()).isEmpty();
        assertThat(effective.questionCount()).isEqualTo(50);
    }

    @Test
    void sourceSubjectSetsSectionScopeWithEveryChild() {
        var effective = compile(rule("s1", MapidRuleType.SOURCE, null, A, B), rule("s2", MapidRuleType.SOURCE, null, C));
        assertThat(effective.knowledgeScope().nodeIds()).containsExactlyInAnyOrder(A, B, C);
        assertThat(effective.knowledgeScope().everyChildInScope()).isTrue();
        assertThat(effective.knowledgeScope().competencyCodes()).containsExactly("SEC");
        assertThat(block(effective, "g2").knowledgeScope().nodeIds()).isEmpty();
    }

    @Test
    void sourceGroupSetsBlockScopeWithEveryChildForSetsOnly() {
        var effective = compile(rule("s1", MapidRuleType.SOURCE, "g2", A, B), rule("s2", MapidRuleType.SOURCE, "g1", C));
        QuestionBlock set = block(effective, "g2");
        assertThat(set.knowledgeScope().nodeIds()).containsExactlyInAnyOrder(A, B);
        assertThat(set.knowledgeScope().everyChildInScope()).isTrue();
        assertThat(set.knowledgeScope().competencyCodes()).containsExactly("BLK");
        QuestionBlock single = block(effective, "g1");
        assertThat(single.knowledgeScope().nodeIds()).containsExactly(C);
        assertThat(single.knowledgeScope().everyChildInScope()).isNull();
        assertThat(effective.knowledgeScope().nodeIds()).isEmpty();
    }

    @Test
    void presentIsARequiredMinimumOfOne() {
        var effective = compile(rule("p1", MapidRuleType.PRESENT, null, A, B), rule("p2", MapidRuleType.PRESENT, "g3", C));
        assertThat(effective.topicQuotas()).singleElement().satisfies(quota -> {
            assertThat(quota.id()).isEqualTo("p1");
            assertThat(quota.nodeIds()).containsExactly(A, B);
            assertThat(quota.count()).isEqualTo(1);
            assertThat(quota.basis()).isEqualTo(QuotaBasis.QUESTION);
            assertThat(quota.enforcement()).isEqualTo(QuotaEnforcement.REQUIRED);
        });
        assertThat(block(effective, "g3").topicQuotas()).singleElement().satisfies(quota -> {
            assertThat(quota.id()).isEqualTo("p2");
            assertThat(quota.count()).isEqualTo(1);
        });
    }

    @Test
    void countAtLeastIsOnlyAMinimum() {
        var compiled = MapidRuleCompiler.compile(section(MapidMode.MATRIX, List.of(
                count("c1", null, MapidComparator.AT_LEAST, 3, null, A),
                count("c2", "g2", MapidComparator.AT_LEAST, 2, null, B))));
        assertThat(compiled.effectiveSection().topicQuotas()).singleElement().satisfies(quota -> {
            assertThat(quota.id()).isEqualTo("c1");
            assertThat(quota.count()).isEqualTo(3);
            assertThat(quota.enforcement()).isEqualTo(QuotaEnforcement.REQUIRED);
        });
        assertThat(block(compiled.effectiveSection(), "g2").topicQuotas()).singleElement()
                .satisfies(quota -> assertThat(quota.count()).isEqualTo(2));
        assertThat(compiled.caps()).isEmpty();
    }

    @Test
    void countExactSubjectIsMinimumPlusSubjectCap() {
        var compiled = MapidRuleCompiler.compile(section(MapidMode.MATRIX, List.of(
                count("c1", null, MapidComparator.EXACT, 3, null, A, B))));
        assertThat(compiled.effectiveSection().topicQuotas()).singleElement().satisfies(quota -> {
            assertThat(quota.count()).isEqualTo(3);
            assertThat(quota.nodeIds()).containsExactly(A, B);
        });
        assertThat(compiled.caps()).containsExactly(new Cap("c1", Cap.Scope.SUBJECT, null, List.of(A, B), 3));
    }

    @Test
    void countExactGroupIsMinimumPlusGroupCap() {
        var compiled = MapidRuleCompiler.compile(section(MapidMode.MATRIX, List.of(
                count("c1", "g1", MapidComparator.EXACT, 4, null, A))));
        assertThat(compiled.effectiveSection().topicQuotas()).isEmpty();
        assertThat(block(compiled.effectiveSection(), "g1").topicQuotas()).singleElement()
                .satisfies(quota -> assertThat(quota.count()).isEqualTo(4));
        assertThat(compiled.caps()).containsExactly(new Cap("c1", Cap.Scope.GROUP, "g1", List.of(A), 4));
    }

    @Test
    void countExactZeroOnlyCaps() {
        var compiled = MapidRuleCompiler.compile(section(MapidMode.MATRIX, List.of(
                count("c1", null, MapidComparator.EXACT, 0, null, A))));
        assertThat(compiled.effectiveSection().topicQuotas()).isEmpty();
        assertThat(compiled.caps()).containsExactly(new Cap("c1", Cap.Scope.SUBJECT, null, List.of(A), 0));
    }

    @Test
    void percentUsesTheScopeUnitsAndRoundsHalfUp() {
        var compiled = MapidRuleCompiler.compile(section(MapidMode.MATRIX, List.of(
                count("subject", null, MapidComparator.AT_LEAST, null, "20", A),
                count("set", "g2", MapidComparator.AT_LEAST, null, "50", B),
                count("single", "g1", MapidComparator.AT_LEAST, null, "10", C),
                count("half", "g3", MapidComparator.EXACT, null, "12.5", A))));
        ExamSection effective = compiled.effectiveSection();
        // section 50 questions -> 10; set group 2 x 3 = 6 units -> 3; single group 15 -> 1.5 -> 2; 12.5% of 4 -> 0.5 -> 1.
        assertThat(effective.topicQuotas()).singleElement().satisfies(q -> assertThat(q.count()).isEqualTo(10));
        assertThat(block(effective, "g2").topicQuotas()).singleElement().satisfies(q -> assertThat(q.count()).isEqualTo(3));
        assertThat(block(effective, "g1").topicQuotas()).singleElement().satisfies(q -> assertThat(q.count()).isEqualTo(2));
        assertThat(block(effective, "g3").topicQuotas()).singleElement().satisfies(q -> assertThat(q.count()).isEqualTo(1));
        assertThat(compiled.caps()).containsExactly(new Cap("half", Cap.Scope.GROUP, "g3", List.of(A), 1));
    }

    @Test
    void percentWinsOverCountAndMissingDenominatorGivesZero() {
        var compiled = MapidRuleCompiler.compile(section(MapidMode.MATRIX, List.of(
                count("c1", null, MapidComparator.AT_LEAST, 7, "10", A))));
        assertThat(compiled.effectiveSection().topicQuotas()).singleElement()
                .satisfies(q -> assertThat(q.count()).isEqualTo(5));
        ExamSection noTotal = new ExamSection("s", "MATH", null, null, null, null, null, List.of(), List.of(), null,
                MapidMode.MATRIX, List.of(count("c2", null, MapidComparator.EXACT, null, "10", A)));
        var empty = MapidRuleCompiler.compile(noTotal);
        assertThat(empty.effectiveSection().topicQuotas()).isEmpty();
        assertThat(empty.caps()).singleElement().satisfies(cap -> assertThat(cap.max()).isZero());
    }

    @Test
    void onlyInGroupExcludesTheNodesFromEveryOtherBlock() {
        var compiled = MapidRuleCompiler.compile(section(MapidMode.MATRIX, List.of(
                new MapidRule("o1", MapidRuleType.ONLY_IN_GROUP, "g2", List.of(A, B), null, null, null),
                new MapidRule("o2", MapidRuleType.ONLY_IN_GROUP, "g1", List.of(B, C), null, null, null))));
        assertThat(compiled.exclusions().keySet()).containsExactlyInAnyOrder("g1", "g2", "g3");
        assertThat(compiled.exclusions().get("g1")).containsExactlyInAnyOrder(A, B);
        assertThat(compiled.exclusions().get("g2")).containsExactlyInAnyOrder(B, C);
        assertThat(compiled.exclusions().get("g3")).containsExactlyInAnyOrder(A, B, C);
        assertThat(compiled.effectiveSection().topicQuotas()).isEmpty();
        assertThat(compiled.caps()).isEmpty();
    }

    @Test
    void onlyInGroupWithCountCompilesAsCountOnTheTargetGroup() {
        var compiled = MapidRuleCompiler.compile(section(MapidMode.MATRIX, List.of(
                new MapidRule("o1", MapidRuleType.ONLY_IN_GROUP, "g2", List.of(A), MapidComparator.AT_LEAST, 2, null),
                new MapidRule("o2", MapidRuleType.ONLY_IN_GROUP, "g1", List.of(B), MapidComparator.EXACT, 1, null))));
        assertThat(block(compiled.effectiveSection(), "g2").topicQuotas()).singleElement().satisfies(quota -> {
            assertThat(quota.id()).isEqualTo("o1");
            assertThat(quota.count()).isEqualTo(2);
        });
        assertThat(block(compiled.effectiveSection(), "g1").topicQuotas()).singleElement()
                .satisfies(quota -> assertThat(quota.count()).isEqualTo(1));
        assertThat(compiled.caps()).containsExactly(new Cap("o2", Cap.Scope.GROUP, "g1", List.of(B), 1));
        assertThat(compiled.exclusions().get("g1")).containsExactly(A);
        assertThat(compiled.exclusions().get("g2")).containsExactly(B);
    }

    @Test
    void ruleNodeSetsStayTogetherAsOneQuota() {
        var effective = compile(count("c1", null, MapidComparator.AT_LEAST, 2, null, A, B, C));
        assertThat(effective.topicQuotas()).singleElement()
                .satisfies(quota -> assertThat(quota.nodeIds()).containsExactly(A, B, C));
    }

    @Test
    void legacyQuotasAreReplacedNotMerged() {
        var effective = compile(rule("p", MapidRuleType.PRESENT, null, A));
        assertThat(effective.topicQuotas()).extracting(TopicQuota::id).containsExactly("p");
        assertThat(block(effective, "g2").topicQuotas()).isEmpty();
    }

    @Test
    void aRuleOfAnUnknownGroupIsSkippedNotThrown() {
        // A saved rule whose group is gone must not break check or preview: it compiles to nothing and the check
        // reports MAPID_RULE_GROUP_MISSING (UNI-2758 BE-3b).
        ExamSection section = section(MapidMode.MATRIX, List.of(
                rule("p", MapidRuleType.PRESENT, "gone", A),
                count("c", "gone", MapidComparator.EXACT, 2, null, B),
                new MapidRule("o", MapidRuleType.ONLY_IN_GROUP, "gone", List.of(C), null, null, null),
                rule("s", MapidRuleType.SOURCE, "gone", A),
                rule("kept", MapidRuleType.PRESENT, null, B)));
        var compiled = MapidRuleCompiler.compile(section);
        assertThat(compiled.caps()).isEmpty();
        assertThat(compiled.exclusions()).isEmpty();
        assertThat(compiled.effectiveSection().topicQuotas()).extracting(TopicQuota::id).containsExactly("kept");
        assertThat(compiled.effectiveSection().blocks()).allSatisfy(block -> {
            assertThat(block.topicQuotas()).isEmpty();
            assertThat(block.knowledgeScope().nodeIds()).isEmpty();
        });
    }

    private static ExamSection compile(MapidRule... rules) {
        return MapidRuleCompiler.compile(section(MapidMode.MATRIX, List.of(rules))).effectiveSection();
    }

    /** Section of 50 questions with legacy scope/quotas on it and on every block (g1 single 15, g2 set 2 x 3, g3 single 4). */
    private static ExamSection section(MapidMode mode, List<MapidRule> rules) {
        return new ExamSection("s", "MATH", 50, new KnowledgeScope(List.of(LEGACY), List.of("SEC"), true),
                null, null, null,
                List.of(block("g1", QuestionType.SHORT_ANSWER, 15, null, null),
                        block("g2", QuestionType.QUESTION_SET, null, 2, 3),
                        block("g3", QuestionType.SINGLE_CHOICE, 4, null, null)),
                List.of(legacyQuota("sectionLegacy")), null, mode, rules);
    }

    private static QuestionBlock block(String id, QuestionType type, Integer count, Integer setCount, Integer setSize) {
        return new QuestionBlock(id, id, type, count, setCount, setSize,
                new KnowledgeScope(List.of(LEGACY), List.of("BLK"), true), null, null, null, false, null,
                List.of(), null, true, false, List.of(), List.of(), null, List.of(), false,
                List.of(legacyQuota("blockLegacy")), null);
    }

    private static TopicQuota legacyQuota(String id) {
        return new TopicQuota(id, id, List.of(LEGACY), 1, QuotaBasis.QUESTION, QuotaEnforcement.REQUIRED,
                List.of(), List.of(), List.of());
    }

    private static MapidRule rule(String id, MapidRuleType type, String groupId, UUID... nodes) {
        return new MapidRule(id, type, groupId, List.of(nodes), null, null, null);
    }

    private static MapidRule count(String id, String groupId, MapidComparator comparator, Integer count,
            String percent, UUID... nodes) {
        return new MapidRule(id, MapidRuleType.COUNT, groupId, List.of(nodes), comparator, count,
                percent == null ? null : new BigDecimal(percent));
    }

    private static QuestionBlock block(ExamSection section, String id) {
        return section.blocks().stream().filter(block -> block.id().equals(id)).findFirst().orElseThrow();
    }
}
