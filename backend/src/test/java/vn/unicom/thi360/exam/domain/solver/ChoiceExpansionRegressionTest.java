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

    @Test
    void witnessesRetainDifferentSectionsWithSameSubjectAndCount() {
        SectionOnPath m1 = choiceSection("M", "m1");
        SectionOnPath m2 = choiceSection("M", "m2");
        SectionOnPath a = choiceSection("A", "a");
        SectionOnPath b = choiceSection("B", "b");
        SubjectChoice first = new SubjectChoice("first", List.of(m1, a), 1, List.of(1));
        SubjectChoice second = new SubjectChoice("second", List.of(m2, b), 1, List.of(1));
        SolvePath path = new SolvePath(0, List.of(), List.of(), List.of(first, second), List.of());
        PathResult result = new PaperSolver().solvePath(path,
                group -> List.of(PathCandidate.standalone(group.groupId(), 1, "SC", "G10", "NB", null)),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.path().picks()).hasSize(2);
        assertThat(result.witness().stream()
                .filter(w -> w.subjectCode().equals("M") && w.resolvedCount() == 1)
                .flatMap(w -> w.groups().stream()).map(GroupWitness::groupId))
                .containsExactlyInAnyOrder("m1", "m2");
    }

    private static SectionOnPath choiceSection(String subject, String id) {
        return new SectionOnPath(subject, id, null, SubjectQuota.NONE,
                List.of(GroupQuota.remainder(id)));
    }
}
