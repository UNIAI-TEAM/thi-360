package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuotaDiagnosticRegressionTest {
    @Test
    void subjectGradeShortageIdentifiesQuota() {
        GroupQuota a = GroupQuota.single("a", 1);
        GroupQuota b = GroupQuota.single("b", 1);
        SubjectQuota quota = new SubjectQuota(null, new MatrixQuota.Separate(
                List.of(new DimensionQuota("G10", 1), new DimensionQuota("G11", 1)), List.of()));
        SectionOnPath section = new SectionOnPath("ENG", "sec", 2, quota, List.of(a, b));
        PathResult result = new PaperSolver().solvePath(SolvePath.of(0, List.of(section)),
                g -> List.of(PathCandidate.standalone(g.groupId(), 1, "SC", "G11", "NB", null)),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.failingQuota()).isEqualTo(new QuotaRef("ENG", "sec", "GRADE", "G10"));
    }
}
