package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GroupPercentRemainderTest {
    enum Dimension { COMPETENCY, GRADE, DIFFICULTY, CELL }

    @ParameterizedTest
    @EnumSource(Dimension.class)
    void eachSubjectPickApportionsRemainderForItsResolvedCount(Dimension dimension) {
        SolveOutcome outcome = solve(dimension, 6, 5);
        assertThat(outcome.status()).isEqualTo(SolveStatus.VALID);
        List<SubjectWitness> witnesses = outcome.results().getFirst().witness();
        assertThat(witnesses).hasSize(4);
        for (SubjectWitness subject : witnesses) {
            GroupWitness remainder = subject.groups().stream()
                    .filter(group -> group.groupId().endsWith("-remainder")).findFirst().orElseThrow();
            int remainderCount = subject.resolvedCount() - 6;
            assertThat(remainder.picks()).hasSize(remainderCount);
            assertThat(remainder.picks().stream().filter(pick -> pick.questionId().contains("-P-")).count())
                    .isEqualTo(remainderCount == 10 ? 5 : 6);
        }
    }

    @ParameterizedTest
    @EnumSource(Dimension.class)
    void elevenQuestionPickFailsWhenOnlyFiveQuestionsMeetFirstHalf(Dimension dimension) {
        SolveOutcome outcome = solve(dimension, 5, 6);
        assertThat(outcome.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(outcome.results().getFirst().failingSelections()).isNotEmpty();
    }

    private SolveOutcome solve(Dimension dimension, int pCount, int qCount) {
        List<SectionOnPath> subjects = List.of(section("A", dimension), section("B", dimension));
        SubjectChoice choice = new SubjectChoice("choice", subjects, 2, List.of(17, 16));
        return new PaperSolver().solveAll(List.of(SolvePart.Choice.of("part", List.of(choice))), group -> {
            String subject = group.groupId().substring(0, 1);
            if (group.groupId().endsWith("-fixed")) {
                return candidates(subject, "fixed", 6, false);
            }
            List<PathCandidate> pool = new ArrayList<>(candidates(subject, "P", pCount, true));
            pool.addAll(candidates(subject, "Q", qCount, false));
            return pool;
        }, SolveMode.check());
    }

    private SectionOnPath section(String code, Dimension dimension) {
        List<CompetencyQuota> competencies = dimension == Dimension.COMPETENCY
                ? List.of(new CompetencyQuota("P", 50, QuotaUnit.PERCENT),
                          new CompetencyQuota("Q", 50, QuotaUnit.PERCENT)) : List.of();
        MatrixQuota matrix = switch (dimension) {
            case COMPETENCY -> null;
            case GRADE -> new MatrixQuota.Separate(List.of(new DimensionQuota("G1", 50),
                    new DimensionQuota("G2", 50)), List.of(), QuotaUnit.PERCENT);
            case DIFFICULTY -> new MatrixQuota.Separate(List.of(), List.of(new DimensionQuota("NB", 50),
                    new DimensionQuota("TH", 50)), QuotaUnit.PERCENT);
            case CELL -> new MatrixQuota.Combined(List.of(new CellQuota("G1", "NB", 50),
                    new CellQuota("G2", "TH", 50)), QuotaUnit.PERCENT);
        };
        GroupQuota remainder = new GroupQuota(code + "-remainder", GroupQuota.GroupKind.SINGLE,
                null, null, null, null, null, matrix, competencies, List.of(), null, null);
        return new SectionOnPath(code, "section-" + code, null, SubjectQuota.NONE,
                List.of(GroupQuota.single(code + "-fixed", 6), remainder));
    }

    private List<PathCandidate> candidates(String subject, String label, int count, boolean first) {
        List<PathCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CountingUnit unit = new CountingUnit(null, "SINGLE_CHOICE", first ? "G1" : "G2",
                    first ? "NB" : "TH", List.of(), List.of(first ? "P" : "Q"), BigDecimal.ONE);
            candidates.add(new PathCandidate(subject + "-" + label + "-" + i, 1, null, null,
                    List.of(unit)));
        }
        return candidates;
    }
}
