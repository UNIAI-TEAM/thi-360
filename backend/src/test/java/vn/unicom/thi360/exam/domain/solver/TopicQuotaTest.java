package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
