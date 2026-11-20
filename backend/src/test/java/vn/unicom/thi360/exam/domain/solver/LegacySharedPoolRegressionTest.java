package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * UNI-2758 R5-1: a legacy structure (no {@code mapidMode}, no caps, no MATRIX quotas) must yield the
 * exact same paper for a seed as the parent {@code a090cf84}, even when a group topic minimum exists.
 * Two single-question groups share the pool {@code [x1, x2]}; the candidate reserved by the group the
 * search visits first decides the other group's pick, so pair ordering is observable.
 */
class LegacySharedPoolRegressionTest {

    private final PaperSolver solver = new PaperSolver();

    private static SectionOnPath section() {
        TopicQuota minX = new TopicQuota("min-x", List.of("X"), 1, List.of(), List.of(), List.of());
        GroupQuota a = new GroupQuota("A", GroupQuota.GroupKind.SINGLE, 1, "SINGLE_CHOICE", null,
                null, null, null, List.of(), List.of(), null, null, null, List.of());
        GroupQuota b = new GroupQuota("B", GroupQuota.GroupKind.SINGLE, 1, "SINGLE_CHOICE", null,
                null, null, null, List.of(), List.of(), null, null, null, List.of(minX));
        return SolverTestFixtures.section("LEG", 2, a, b);
    }

    private static CandidateSource source() {
        List<PathCandidate> shared = List.of(single("x1"), single("x2"));
        return g -> shared;
    }

    private static PathCandidate single(String id) {
        return new PathCandidate(id, 1, null, null, List.of(
                new CountingUnit(null, "SINGLE_CHOICE", null, null, List.of("X"), List.of(), null)));
    }

    private Map<String, String> picksByGroup(long seed) {
        PathResult result = solver.solvePath(SolverTestFixtures.path(section()), source(),
                SolveMode.generate(seed));
        assertThat(result.status()).as("seed %s reason %s", seed, result.reason())
                .isEqualTo(SolveStatus.VALID);
        return result.witness().getFirst().groups().stream().collect(Collectors.toMap(
                GroupWitness::groupId,
                g -> g.picks().getFirst().questionId()));
    }

    @Test
    void seedZeroKeepsParentPicksForLegacyGroupQuota() {
        // Captured on a090cf84 (bef5/capture-a090cf84.log): seed 0 → A=x1, B=x2.
        assertThat(picksByGroup(0)).containsExactlyInAnyOrderEntriesOf(Map.of("A", "x1", "B", "x2"));
    }

    @Test
    void seed4096KeepsSharedPoolSwappedPick() {
        // Same outcome on both revisions (bef5/capture-*.log): pins that generation still varies by seed.
        assertThat(picksByGroup(4096))
                .containsExactlyInAnyOrderEntriesOf(Map.of("A", "x2", "B", "x1"));
    }
}
