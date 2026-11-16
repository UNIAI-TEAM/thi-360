package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SelectionResultTest {
    @Test
    void keepsEachSubjectChoiceTupleAndItsOwnReasonAndWitness() {
        SolvePart part = SolvePart.Choice.of("part", List.of(choice("a"), choice("b")), 2);
        SolveOutcome outcome = new PaperSolver().solveAll(List.of(part), group -> {
            if (group.groupId().equals("a-group-1")) return List.of();
            return List.of(PathCandidate.standalone(group.groupId(), 1, "SINGLE_CHOICE", "G10", "NB", null));
        }, SolveMode.check());

        PathResult path = outcome.results().getFirst();
        assertThat(path.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(path.selections()).hasSize(4);
        assertThat(path.selections().stream().map(SelectionResult::status))
                .containsExactly(SolveStatus.VALID, SolveStatus.VALID, SolveStatus.INVALID, SolveStatus.INVALID);
        assertThat(path.selections().getFirst().picks().stream()
                .map(SubjectPick::subjectCodes)).containsExactly(List.of("a0"), List.of("b0"));
        assertThat(path.selections().getFirst().witness()).hasSize(2);
        assertThat(path.selections().get(2).subjectCode()).isEqualTo("a1");
        assertThat(path.selections().get(2).reason()).isEqualTo(SolveReason.INSUFFICIENT_SOURCE);
        assertThat(path.selections().get(2).picks().stream()
                .map(SubjectPick::subjectCodes)).containsExactly(List.of("a1"), List.of("b0"));
    }

    private static SubjectChoice choice(String id) {
        List<SectionOnPath> sections = IntStream.range(0, 2).mapToObj(i ->
                new SectionOnPath(id + i, id + "-section-" + i, null, SubjectQuota.NONE,
                        List.of(GroupQuota.remainder(id + "-group-" + i)))).toList();
        return new SubjectChoice(id, sections, 1, List.of(1));
    }
}
