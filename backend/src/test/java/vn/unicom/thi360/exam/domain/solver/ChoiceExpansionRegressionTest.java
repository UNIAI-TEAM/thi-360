package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChoiceExpansionRegressionTest {
    @Test
    void unopenedChoiceMustNotProduceEmptyValidPaper() {
        SectionOnPath a = new SectionOnPath("A", "a", null, SubjectQuota.NONE,
                List.of(GroupQuota.remainder("ga")));
        SectionOnPath b = new SectionOnPath("B", "b", null, SubjectQuota.NONE,
                List.of(GroupQuota.remainder("gb")));
        SubjectChoice choice = new SubjectChoice("choice", List.of(a, b), 1, List.of(1));
        PaperSolver solver = new PaperSolver();
        for (List<List<SubjectPick>> picks : List.of(List.<List<SubjectPick>>of(),
                List.of(List.<SubjectPick>of()))) {
            SolvePath path = new SolvePath(0, List.of(), List.of(), List.of(choice), picks);
            PathResult result = solver.solvePath(path, g -> List.of(), SolveMode.check());
            assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        }
    }
}
