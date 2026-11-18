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

    private PathCandidate setOfSize(String id, int size, String grade, String difficulty) {
        CountingUnit[] units = new CountingUnit[size];
        for (int i = 0; i < size; i++) units[i] = unit(id + ":" + i, SC, grade, difficulty);
        return set(id, units);
    }

    private PathCandidate setOfSize(String id, int size) {
        return setOfSize(id, size, G10, NB);
    }

    @Test
    void choosesWholeSetsOfMixedSizes() {
        var pool = List.of(setOfSize("a", 3), setOfSize("b", 4), setOfSize("c", 5),
                setOfSize("d", 3), setOfSize("e", 4), setOfSize("f", 5));
        var result = new PaperSolver().solvePath(path(section("MATH", 18, flexible(18))),
                source(pool), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        var byId = pool.stream().collect(java.util.stream.Collectors.toMap(PathCandidate::questionId, c -> c));
        assertThat(result.witness().getFirst().groups().getFirst().picks().stream()
                .mapToInt(p -> byId.get(p.questionId()).units().size()).sum()).isEqualTo(18);
    }

    @Test
    void choosesWholeMixedSetsWithExactChildTotalAndSectionMatrix() {
        var pool = List.of(setOfSize("a", 3, G10, NB), setOfSize("b", 3, G10, NB),
                setOfSize("c", 3, G10, NB), setOfSize("d", 3, G11, TH),
                setOfSize("e", 3, G11, TH), setOfSize("f", 3, G11, TH),
                setOfSize("g", 3, G10, TH), setOfSize("h", 3, G11, NB));
        var quota = new SubjectQuota(null, new MatrixQuota.Separate(
                List.of(dim(G10, 9), dim(G11, 9)), List.of(dim(NB, 9), dim(TH, 9))));
        var result = new PaperSolver().solvePath(path(section("MATH", 18, quota, flexible(18))),
                source(pool), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        var selected = result.witness().getFirst().groups().getFirst().picks();
        Map<String, PathCandidate> byId = pool.stream().collect(java.util.stream.Collectors.toMap(PathCandidate::questionId, c -> c));
        var units = selected.stream().flatMap(p -> byId.get(p.questionId()).units().stream()).toList();
        assertThat(units).hasSize(18);
        assertThat(units.stream().filter(u -> G10.equals(u.gradeCode())).count()).isEqualTo(9);
        assertThat(units.stream().filter(u -> G11.equals(u.gradeCode())).count()).isEqualTo(9);
        assertThat(units.stream().filter(u -> NB.equals(u.difficultyCode())).count()).isEqualTo(9);
        assertThat(units.stream().filter(u -> TH.equals(u.difficultyCode())).count()).isEqualTo(9);
    }

    @Test
    void enoughChildrenButNoDimensionFeasibleSubsetIsInvalid() {
        var gradeShort = List.of(setOfSize("a", 3, G10, NB), setOfSize("b", 3, G10, NB),
                setOfSize("c", 3, G10, NB), setOfSize("d", 3, G10, TH),
                setOfSize("e", 3, G10, TH), setOfSize("f", 3, G10, TH),
                setOfSize("g", 3, G11, NB), setOfSize("h", 3, G11, TH));
        var difficultyShort = List.of(setOfSize("a", 3, G10, NB), setOfSize("b", 3, G10, NB),
                setOfSize("c", 3, G10, NB), setOfSize("d", 3, G11, NB),
                setOfSize("e", 3, G11, NB), setOfSize("f", 3, G11, NB),
                setOfSize("g", 3, G10, TH), setOfSize("h", 3, G11, TH));
        var quota = new SubjectQuota(null, new MatrixQuota.Separate(
                List.of(dim(G10, 9), dim(G11, 9)), List.of(dim(NB, 9), dim(TH, 9))));
        for (var pool : List.of(gradeShort, difficultyShort)) {
            var result = new PaperSolver().solvePath(path(section("MATH", 18, quota, flexible(18))),
                    source(pool), SolveMode.check());
            assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        }
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
