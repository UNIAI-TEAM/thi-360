package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * UNI-2758 R5-1: a legacy structure (no {@code mapidMode}, no caps, no MATRIX quotas) must yield the
 * exact same paper for a seed as the parent {@code a090cf84}, even when a group topic minimum exists
 * or the merged sub-problem gains a same-subject sibling whose section mode is MATRIX. Two
 * single-question groups share the pool {@code [x1, x2]}; the candidate reserved by the group the
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
        List<PathCandidate> disjoint = List.of(single("n", "N"));
        return g -> "C".equals(g.groupId()) ? disjoint : shared;
    }

    /** A same-subject sibling in MATRIX mode; {@code caps} carry its compiled EXACT rules. */
    private static SectionOnPath matrixSibling(List<TopicCap> caps) {
        GroupQuota c = new GroupQuota("C", GroupQuota.GroupKind.SINGLE, 1, "SINGLE_CHOICE", null,
                null, null, null, List.of(), List.of(), null, null, null, List.of());
        SubjectQuota quota = new SubjectQuota(null, null, List.of(), List.of(), caps, true);
        return new SectionOnPath("LEG", "LEG-C", 1, quota, List.of(c));
    }

    private static PathCandidate single(String id) {
        return single(id, "X");
    }

    private static PathCandidate single(String id, String node) {
        return new PathCandidate(id, 1, null, null, List.of(
                new CountingUnit(null, "SINGLE_CHOICE", null, null, List.of(node), List.of(), null)));
    }

    private Map<String, String> picksByGroup(long seed, SectionOnPath... sections) {
        PathResult result = solver.solvePath(SolverTestFixtures.path(sections), source(),
                SolveMode.generate(seed));
        assertThat(result.status()).as("seed %s reason %s", seed, result.reason())
                .isEqualTo(SolveStatus.VALID);
        return result.witness().getFirst().groups().stream().collect(Collectors.toMap(
                GroupWitness::groupId,
                g -> g.picks().getFirst().questionId()));
    }

    @Test
    void seedZeroKeepsParentPicksForLegacyGroupQuota() {
        assertThat(picksByGroup(0, section()))
                .containsExactlyInAnyOrderEntriesOf(Map.of("A", "x1", "B", "x2"));
    }

    @Test
    void seed4096KeepsSharedPoolSwappedPick() {
        // Same outcome on both revisions (bef5/capture-*.log): pins that generation still varies by seed.
        assertThat(picksByGroup(4096, section()))
                .containsExactlyInAnyOrderEntriesOf(Map.of("A", "x2", "B", "x1"));
    }

    @Test
    void emptyMatrixSiblingKeepsLegacyPicks() {
        // review-be-6 R5-1: MATRIX mode with no rule compiles to no quota and no cap; mode alone must
        // not switch the legacy groups' ordering. Captured on a090cf84 (bef6/capture-a090cf84.log).
        assertThat(picksByGroup(0, section(), matrixSibling(List.of())))
                .containsExactlyInAnyOrderEntriesOf(Map.of("A", "x1", "B", "x2", "C", "n"));
    }

    @Test
    void cappedMatrixSiblingKeepsLegacyPicks() {
        // A MATRIX cap enables the optimisation, but only MATRIX-compiled minima promote a group;
        // the legacy minimum of B stays out of the promotion set.
        assertThat(picksByGroup(0, section(),
                matrixSibling(List.of(new TopicCap("cap-q", null, List.of("Q"), 0)))))
                .containsExactlyInAnyOrderEntriesOf(Map.of("A", "x1", "B", "x2", "C", "n"));
    }

    @Test
    void matrixCompiledGroupMinimumPromotes() {
        // The positive control (review-be-6: "no-cap group-minimum probe"): a minimum compiled from
        // MAPID MATRIX promotes its group — B reserves x1 first, so A takes x2.
        TopicQuota compiledMin = new TopicQuota("min-x", List.of("X"), 1, List.of(), List.of(),
                List.of(), true);
        GroupQuota a = new GroupQuota("A", GroupQuota.GroupKind.SINGLE, 1, "SINGLE_CHOICE", null,
                null, null, null, List.of(), List.of(), null, null, null, List.of());
        GroupQuota b = new GroupQuota("B", GroupQuota.GroupKind.SINGLE, 1, "SINGLE_CHOICE", null,
                null, null, null, List.of(), List.of(), null, null, null, List.of(compiledMin));
        assertThat(picksByGroup(0, SolverTestFixtures.section("LEG", 2, a, b)))
                .containsExactlyInAnyOrderEntriesOf(Map.of("A", "x2", "B", "x1"));
    }
}
