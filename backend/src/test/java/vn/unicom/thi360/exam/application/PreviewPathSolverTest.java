package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PaperSolver;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;

class PreviewPathSolverTest {
    @Test
    void choiceIsSolvedAtFullCountBeforeRemovingOne() {
        GroupQuota a = GroupQuota.single("a", 2);
        GroupQuota b = GroupQuota.single("b", 2);
        SectionOnPath first = new SectionOnPath("A", "a", null, null, List.of(a), "NATURAL");
        SectionOnPath second = new SectionOnPath("B", "b", null, null, List.of(b), "NATURAL");
        SubjectChoice choice = new SubjectChoice("choice", List.of(first, second), 2, List.of(2, 1));
        SubjectPick pick = new SubjectPick("choice", List.of(first, second), List.of(2, 1));
        SolvePath path = new SolvePath(0, List.of(), List.of(), List.of(choice), List.of(List.of(pick)));
        var pools = Map.of("a", List.of(question("a1"), question("a2")),
                "b", List.of(question("b1"), question("b2")));

        var result = PreviewPathSolver.solve(new PaperSolver(), path, group -> pools.get(group.groupId()), 7);
        assertThat(result.path().status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.dropped()).hasSize(1);
        assertThat(result.path().witness()).extracting(w -> w.resolvedCount()).containsExactlyInAnyOrder(2, 1);
        assertThat(result.path().witness().stream().flatMap(w -> w.groups().stream())
                .flatMap(group -> group.picks().stream())).hasSize(3)
                .doesNotContain(result.dropped().getFirst());
    }

    private static PathCandidate question(String id) {
        return PathCandidate.standalone(id, 1, "SINGLE_CHOICE", null, null, null);
    }
}
