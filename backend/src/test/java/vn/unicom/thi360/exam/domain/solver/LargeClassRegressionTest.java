package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LargeClassRegressionTest {
    @Test
    void twentyThousandEquivalentCandidatesDoNotOverflowStack() {
        GroupQuota a = GroupQuota.single("a", 1);
        GroupQuota b = GroupQuota.single("b", 3);
        SubjectQuota quota = new SubjectQuota(null, new MatrixQuota.Separate(
                List.of(new DimensionQuota("G10", 2), new DimensionQuota("G11", 2)),
                List.of(new DimensionQuota("NB", 2), new DimensionQuota("TH", 2))));
        SectionOnPath section = new SectionOnPath("MATH", "s", 4, quota, List.of(a, b));
        List<PathCandidate> poolA = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            poolA.add(PathCandidate.standalone("a" + i, 1, "SC", "G10", "NB", null));
        }
        List<PathCandidate> poolB = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            poolB.add(PathCandidate.standalone("b10-" + i, 1, "SC", "G10", "TH", null));
            poolB.add(PathCandidate.standalone("b11-" + i, 1, "SC", "G11", "NB", null));
        }
        PathResult result = new PaperSolver().solvePath(SolvePath.of(0, List.of(section)),
                group -> group == a ? poolA : poolB, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    void equivalentIndependentMembersDoNotRepeatIdentityConflictSearch() {
        GroupQuota a = GroupQuota.single("a", 1);
        GroupQuota b = GroupQuota.single("b", 1);
        GroupQuota c = GroupQuota.single("c", 1);
        SubjectQuota quota = new SubjectQuota(null, new MatrixQuota.Separate(
                List.of(new DimensionQuota("G10", 1), new DimensionQuota("G11", 2)),
                List.of(new DimensionQuota("NB", 1), new DimensionQuota("TH", 2))));
        SectionOnPath section = new SectionOnPath("MATH", "s", 3, quota, List.of(a, b, c));
        List<PathCandidate> first = new ArrayList<>();
        for (int i = 0; i < 19_999; i++) {
            first.add(PathCandidate.standalone("a" + i, 1, "SC", "G10", "NB", null));
        }
        // A can supply a second G11 identity but must take G10; B and C contend for "shared".
        first.add(PathCandidate.standalone("alternate", 1, "SC", "G11", "TH", null));
        PathCandidate shared = PathCandidate.standalone("shared", 1, "SC", "G11", "TH", null);
        PathResult result = new PaperSolver().solvePath(SolvePath.of(0, List.of(section)),
                group -> group == a ? first : List.of(shared), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
    }
}
