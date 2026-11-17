package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class B10PathRegressionTest {
    @Test
    void twelveCasesIdentifyOnlyInsufficientSeventeenQuestionSelections() {
        List<SectionOnPath> sections = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String code = "S" + i;
            sections.add(new SectionOnPath(code, code, null, SubjectQuota.NONE,
                    List.of(GroupQuota.remainder(code)), i < 3 ? "NATURAL" : "SOCIAL"));
        }
        SubjectChoice science = new SubjectChoice("science", sections, 3, List.of(17, 17, 16));
        SolvePart part = SolvePart.Choice.of("part", List.of(science));
        CandidateSource source = g -> {
            int count = g.groupId().equals("S2") ? 16 : 17;
            List<PathCandidate> pool = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                pool.add(PathCandidate.standalone(g.groupId() + "-" + i, 1, "SC", "G", "D", null));
            }
            return pool;
        };
        SolveOutcome outcome = new PaperSolver().solveAll(List.of(part), source, SolveMode.check());
        assertThat(outcome.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(outcome.stats().pathsEnumerated()).isEqualTo(10);
        assertThat(outcome.stats().subproblemsSolved()).isLessThanOrEqualTo(10);
        PathResult result = outcome.results().get(0);
        assertThat(result.path().picks().get(0)).hasSize(12);
        List<SubjectPick> expected = result.path().picks().get(0).stream().filter(p -> {
            int index = p.subjectCodes().indexOf("S2");
            return index >= 0 && p.counts().get(index) == 17;
        }).toList();
        assertThat(expected).isNotEmpty();
        assertThat(result.failingSelections()).containsExactlyElementsOf(expected);
    }
}
