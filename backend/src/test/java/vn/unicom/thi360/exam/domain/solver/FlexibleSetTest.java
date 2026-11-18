package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlexibleSetTest {
    private GroupQuota flexible(int count) {
        return new GroupQuota("sets", GroupQuota.GroupKind.FLEXIBLE_SET, count, null, null, null,
                SC, null, List.of(), List.of(), null, null);
    }

    private PathCandidate setOfSize(String id, int size) {
        CountingUnit[] units = new CountingUnit[size];
        for (int i = 0; i < size; i++) units[i] = unit(id + ":" + i, SC, G10, NB);
        return set(id, units);
    }

    @Test
    void choosesWholeMixedSetsWithExactChildTotalAndSectionMatrix() {
        var pool = List.of(setOfSize("a", 3), setOfSize("b", 4), setOfSize("c", 5),
                setOfSize("d", 3), setOfSize("e", 4), setOfSize("f", 5));
        var quota = new SubjectQuota(null, new MatrixQuota.Separate(
                List.of(dim(G10, 18)), List.of(dim(NB, 18))));
        var result = new PaperSolver().solvePath(path(section("MATH", 18, quota, flexible(18))),
                source(pool), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        var selected = result.witness().getFirst().groups().getFirst().picks();
        Map<String, PathCandidate> byId = pool.stream().collect(java.util.stream.Collectors.toMap(PathCandidate::questionId, c -> c));
        assertThat(selected.stream().mapToInt(p -> byId.get(p.questionId()).units().size()).sum()).isEqualTo(18);
        assertThat(selected.stream().flatMap(p -> byId.get(p.questionId()).units().stream()))
                .allSatisfy(u -> { assertThat(u.gradeCode()).isEqualTo(G10); assertThat(u.difficultyCode()).isEqualTo(NB); });
    }

    @Test
    void uncomposableTotalReportsGroupShortage() {
        var result = new PaperSolver().solvePath(path(section("MATH", 5, flexible(5))),
                source(List.of(setOfSize("a", 3), setOfSize("b", 3))), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.reason()).isEqualTo(SolveReason.INSUFFICIENT_SOURCE);
        assertThat(result.groupId()).isEqualTo("sets");
    }
}
