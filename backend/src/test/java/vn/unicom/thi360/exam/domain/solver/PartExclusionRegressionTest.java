package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PartExclusionRegressionTest {
    @Test
    void excludesAlreadyUsedQuestionsAndRejectsInfeasiblePart() {
        GroupQuota group = GroupQuota.single("g", 1);
        SectionOnPath section = new SectionOnPath("M", "s", 1, SubjectQuota.NONE, List.of(group));
        SolvePath part = SolvePath.of(0, List.of(section));
        PathCandidate q1 = PathCandidate.standalone("q1", 1, "SC", "G10", "NB", null);
        PathCandidate q2 = PathCandidate.standalone("q2", 1, "SC", "G10", "NB", null);
        PaperSolver solver = new PaperSolver();
        PathResult result = solver.solvePartExcluding(part, g -> List.of(q1, q2),
                SolveMode.generate(5), Set.of("q1"));
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.witness().get(0).groups().get(0).picks())
                .containsExactly(new QuestionPick("q2", 1));
        PathResult impossible = solver.solvePartExcluding(part, g -> List.of(q1, q2),
                SolveMode.generate(5), Set.of("q1", "q2"));
        assertThat(impossible.status()).isEqualTo(SolveStatus.INVALID);
    }
}
