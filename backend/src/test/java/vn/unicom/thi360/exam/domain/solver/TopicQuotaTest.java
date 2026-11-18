package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TopicQuotaTest {
    private final PaperSolver solver = new PaperSolver();

    @Test
    void overlappingQuotasRequireDistinctUnitsAndExposeAssignments() {
        SubjectQuota quotas = new SubjectQuota(null, null, List.of(quota("a", "A"), quota("b", "B")));
        SectionOnPath section = SolverTestFixtures.section("MATH", 2, quotas, GroupQuota.single("g", 2));
        PathCandidate both = candidate("both", "A", "B");
        PathCandidate neither = candidate("neither");
        PathResult impossible = solve(section, List.of(both, neither));
        assertThat(impossible.status()).isEqualTo(SolveStatus.INVALID);

        PathResult result = solve(section, List.of(both, candidate("a", "A"), neither));
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        List<QuestionPick> picks = result.witness().getFirst().groups().getFirst().picks();
        assertThat(picks).extracting(QuestionPick::questionId).containsExactlyInAnyOrder("both", "a");
        assertThat(picks.stream().flatMap(p -> p.topicAssignments().stream()).map(TopicAssignment::quotaId))
                .containsExactlyInAnyOrder("a", "b");
        assertThat(picks).allMatch(p -> p.topicAssignments().size() == 1);
    }

    @Test
    void topicCountsAreMinimaAndEligibilityRespectsGradeAndGroup() {
        TopicQuota q = new TopicQuota("a", List.of("A"), 1, List.of("G12"), List.of("HARD"), List.of("g"));
        SectionOnPath section = SolverTestFixtures.section("MATH", 2,
                new SubjectQuota(null, null, List.of(q)), GroupQuota.single("g", 2));
        PathCandidate one = classified("one", "G12", "HARD");
        PathCandidate two = classified("two", "G12", "HARD");
        PathResult result = solve(section, List.of(one, two));
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.witness().getFirst().groups().getFirst().picks()).hasSize(2);
        assertThat(solve(section, List.of(classified("bad", "G11", "HARD"),
                classified("bad2", "G12", "EASY"))).status()).isEqualTo(SolveStatus.INVALID);
    }

    private PathResult solve(SectionOnPath section, List<PathCandidate> candidates) {
        return solver.solvePath(SolverTestFixtures.path(section), g -> candidates, SolveMode.check());
    }

    @Test
    void sectionAndGroupAssignmentsAreIndependentAndSeededResultsRepeat() {
        TopicQuota sectionTopic = quota("section-topic", "A");
        TopicQuota groupTopic = quota("group-topic", "A");
        GroupQuota group = new GroupQuota("g", GroupQuota.GroupKind.SINGLE, 1, null, null,
                null, null, null, List.of(), List.of(), null, null, null, List.of(groupTopic));
        SectionOnPath section = SolverTestFixtures.section("MATH", 1,
                new SubjectQuota(null, null, List.of(sectionTopic)), group);
        List<PathCandidate> candidates = List.of(candidate("a", "A"), candidate("b", "A"));
        var first = solver.solvePath(SolverTestFixtures.path(section), g -> candidates, SolveMode.generate(73));
        var second = solver.solvePath(SolverTestFixtures.path(section), g -> candidates, SolveMode.generate(73));
        assertThat(first.status()).isEqualTo(SolveStatus.VALID);
        assertThat(first.witness()).isEqualTo(second.witness());
        assertThat(first.witness().getFirst().groups().getFirst().picks().getFirst().topicAssignments())
                .extracting(TopicAssignment::quotaId).containsExactlyInAnyOrder("section-topic", "group-topic");
    }

    @Test
    void setChildrenCanSatisfyDifferentTopics() {
        var section = SolverTestFixtures.section("MATH", 2,
                new SubjectQuota(null, null, List.of(quota("a", "A"), quota("b", "B"))),
                GroupQuota.set("set", 1, 2, null));
        var candidate = new PathCandidate("set1", 1, "set1", null, List.of(
                new CountingUnit("child1", "SINGLE_CHOICE", null, null, List.of("A"), List.of(), null),
                new CountingUnit("child2", "SINGLE_CHOICE", null, null, List.of("B"), List.of(), null)));
        var result = solve(section, List.of(candidate));
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.witness().getFirst().groups().getFirst().picks().getFirst().topicAssignments())
                .extracting(TopicAssignment::unitIndex).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void groupQuotasDoNotDoubleCountOneQuestion() {
        GroupQuota group = new GroupQuota("g", GroupQuota.GroupKind.SINGLE, 2, null, null,
                null, null, null, List.of(), List.of(), null, null, null,
                List.of(quota("a", "A"), quota("b", "B")));
        var section = SolverTestFixtures.section("MATH", 2, group);
        // BE4B-R1-02: chỉ tiêu chủ đề của nhóm là chỉ tiêu riêng → thất bại phía nhóm báo
        // NO_JOINT_SOLUTION, không phải SUBJECT_QUOTA_INFEASIBLE
        var infeasible = solve(section, List.of(candidate("both", "A", "B"), candidate("none")));
        assertThat(infeasible.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(infeasible.reason()).isEqualTo(SolveReason.NO_JOINT_SOLUTION);
        assertThat(solve(section, List.of(candidate("both", "A", "B"), candidate("b", "B"))).status())
                .isEqualTo(SolveStatus.VALID);
    }

    @Test
    void slotChildrenShareParentTopicQuota() {
        var parent = new SlotParentQuota("parent", null, List.of(), List.of(),
                List.of(new TopicQuota("topic", List.of("A"), 2, List.of(), List.of(), List.of())));
        var first = new GroupQuota("slot1", GroupQuota.GroupKind.SET, null, null, 1, 1,
                null, null, List.of(), List.of(), null, null, parent);
        var second = new GroupQuota("slot2", GroupQuota.GroupKind.SET, null, null, 1, 1,
                null, null, List.of(), List.of(), null, null, parent);
        var section = SolverTestFixtures.section("MATH", 2, first, second);
        var a = new PathCandidate("set1", 1, "set1", null,
                List.of(new CountingUnit("child1", "SINGLE_CHOICE", null, null, List.of("A"), List.of(), null)));
        var b = new PathCandidate("set2", 1, "set2", null,
                List.of(new CountingUnit("child2", "SINGLE_CHOICE", null, null, List.of("A"), List.of(), null)));
        var result = solver.solvePath(SolverTestFixtures.path(section),
                g -> List.of(g.groupId().equals("slot1") ? a : b), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.witness().getFirst().groups().stream()
                .flatMap(g -> g.picks().stream()).flatMap(p -> p.topicAssignments().stream()))
                .hasSize(2).allMatch(a2 -> a2.scopeId().equals("parent"));
    }



    @Test
    void failedMemoKeepsScopedTopicSupplyAcrossGroups() {
        // BE4B-R1-01: hai nhóm cùng mẫu bit chủ đề nhưng khác mức tối thiểu, dùng chung nguồn
        // ứng viên; nhóm sau không có chỉ tiêu. Nhánh g1={a,b} thất bại vì g2 chỉ còn một A;
        // nghiệm duy nhất g1={a,n}, g2={b,c} phải không bị memo của trạng thái thất bại cắt.
        var g1 = groupWithTopics("g1", 2, new TopicQuota("q1", List.of("A"), 1,
                List.of(), List.of(), List.of()));
        var g2 = groupWithTopics("g2", 2, new TopicQuota("q2", List.of("A"), 2,
                List.of(), List.of(), List.of()));
        var g3 = GroupQuota.single("g3", 1);
        var section = SolverTestFixtures.section("MATH", 5, g1, g2, g3);
        List<PathCandidate> shared = List.of(candidate("a", "A"), candidate("b", "A"),
                candidate("c", "A"), candidate("n"));
        var source = SolverTestFixtures.source(Map.of(
                "g1", shared, "g2", shared, "g3", List.of(candidate("z"))));
        for (SolveMode mode : List.of(SolveMode.check(), SolveMode.generate(7),
                SolveMode.generate(42))) {
            var result = solver.solvePath(SolverTestFixtures.path(section), source, mode);
            assertThat(result.status()).as("mode %s", mode).isEqualTo(SolveStatus.VALID);
            var groups = result.witness().getFirst().groups();
            assertThat(groups.stream().flatMap(g -> g.picks().stream())
                    .map(QuestionPick::questionId)).containsExactlyInAnyOrder(
                            "a", "b", "c", "n", "z");
            assertThat(groups.get(0).picks().stream()
                    .flatMap(p -> p.topicAssignments().stream())
                    .filter(a -> a.quotaId().equals("q1"))).hasSize(1);
            assertThat(groups.get(1).picks().stream()
                    .flatMap(p -> p.topicAssignments().stream())
                    .filter(a -> a.quotaId().equals("q2"))).hasSize(2);
        }
    }

    @Test
    void failedMemoKeepsScopedTopicSupplyAcrossSections() {
        // BE4B-R1-01: hai section cùng môn (một bài toán con), cùng mẫu bit chủ đề cấp section
        // nhưng khác mức tối thiểu — phản ví dụ tương đương qua ranh giới section.
        var s1 = new SectionOnPath("MATH", "s1", 2,
                new SubjectQuota(null, null, List.of(new TopicQuota("s1q", List.of("A"), 1,
                        List.of(), List.of(), List.of()))),
                List.of(GroupQuota.single("g1", 2)));
        var s2 = new SectionOnPath("MATH", "s2", 3,
                new SubjectQuota(null, null, List.of(new TopicQuota("s2q", List.of("A"), 2,
                        List.of(), List.of(), List.of()))),
                List.of(GroupQuota.single("g2", 2), GroupQuota.single("h2", 1)));
        List<PathCandidate> shared = List.of(candidate("a", "A"), candidate("b", "A"),
                candidate("c", "A"), candidate("n"));
        var source = SolverTestFixtures.source(Map.of(
                "g1", shared, "g2", shared, "h2", List.of(candidate("z"))));
        var result = solver.solvePath(SolverTestFixtures.path(s1, s2), source, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        var groups = result.witness().getFirst().groups();
        assertThat(groups.stream().flatMap(g -> g.picks().stream())
                .map(QuestionPick::questionId)).containsExactlyInAnyOrder(
                        "a", "b", "c", "n", "z");
        assertThat(groups.get(0).picks().stream()
                .flatMap(p -> p.topicAssignments().stream())
                .filter(a -> a.quotaId().equals("s1q"))).hasSize(1);
        assertThat(groups.get(1).picks().stream()
                .flatMap(p -> p.topicAssignments().stream())
                .filter(a -> a.quotaId().equals("s2q"))).hasSize(2);
    }

    private static GroupQuota groupWithTopics(String id, int count, TopicQuota... quotas) {
        return new GroupQuota(id, GroupQuota.GroupKind.SINGLE, count, null, null, null, null,
                null, List.of(), List.of(), null, null, null, List.of(quotas));
    }
    private static TopicQuota quota(String id, String node) {
        return new TopicQuota(id, List.of(node), 1, List.of(), List.of(), List.of());
    }

    private static PathCandidate candidate(String id, String... nodes) {
        return new PathCandidate(id, 1, null, null,
                List.of(new CountingUnit(null, "SINGLE_CHOICE", "G12", "HARD", List.of(nodes), List.of(), null)));
    }

    private static PathCandidate classified(String id, String grade, String difficulty) {
        return new PathCandidate(id, 1, null, null,
                List.of(new CountingUnit(null, "SINGLE_CHOICE", grade, difficulty, List.of("A"), List.of(), null)));
    }
}
