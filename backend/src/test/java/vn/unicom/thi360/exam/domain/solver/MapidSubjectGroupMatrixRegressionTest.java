package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HSA Toán matrix plus subject EXACT and short-answer AT_LEAST on the same node (UNI-2758 F9). */
class MapidSubjectGroupMatrixRegressionTest {
    @Test
    void overlappingSubjectCapAndGroupMinimumWithHsaMatrix() {
        TopicQuota subjectMin = new TopicQuota("exact", List.of("X"), 3, List.of(), List.of(), List.of());
        TopicQuota groupMin = new TopicQuota("min", List.of("X"), 2, List.of(), List.of(), List.of());
        MatrixQuota matrix = new MatrixQuota.Separate(
                List.of(new DimensionQuota("G10", 3), new DimensionQuota("G11", 20), new DimensionQuota("G12", 27)),
                List.of(new DimensionQuota("NB", 2), new DimensionQuota("TH", 21),
                        new DimensionQuota("VD", 20), new DimensionQuota("VDC", 7)));
        SubjectQuota quota = new SubjectQuota(null, matrix, List.of(subjectMin), List.of(),
                List.of(new TopicCap("exact", null, List.of("X"), 3)));
        GroupQuota sc = new GroupQuota("SC", GroupQuota.GroupKind.SINGLE, 32, "SINGLE_CHOICE", null, null, null,
                null, List.of(), List.of(), null, null);
        GroupQuota sa = new GroupQuota("SA", GroupQuota.GroupKind.SINGLE, 15, "SHORT_ANSWER", null, null, null,
                null, List.of(), List.of(), null, null, null, List.of(groupMin));
        GroupQuota set = GroupQuota.set("SET", 1, 3, "SINGLE_CHOICE");
        SectionOnPath section = new SectionOnPath("MATH", "SEC", 50, quota, List.of(sc, sa, set));
        List<PathCandidate> scPool = singles("sc", "SINGLE_CHOICE");
        List<PathCandidate> saPool = singles("sa", "SHORT_ANSWER");
        List<CountingUnit> children = new ArrayList<>();
        for (int i = 0; i < 3; i++) children.add(new CountingUnit("child" + i, "SINGLE_CHOICE", "G10", "TH", List.of(), List.of(), null));
        List<PathCandidate> sets = List.of(new PathCandidate("set", 1, "set", null, children));
        CandidateSource source = g -> switch (g.groupId()) { case "SC" -> scPool; case "SA" -> saPool; default -> sets; };
        long start = System.nanoTime();
        SolverLimits limits = new SolverLimits(200, 20_000, Duration.ofSeconds(2), 8);
        SolveOutcome result = new PaperSolver(limits, Clock.systemUTC())
                .solveAll(List.of(SolverTestFixtures.part("P", section)), source, SolveMode.generate(1));
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("PERF subject-group-matrix ms=" + millis + " status=" + result.status()
                + " reason=" + result.reason());
        assertThat(result.status()).as("solver reason: %s", result.reason()).isEqualTo(SolveStatus.VALID);
        assertThat(millis).isLessThan(2000);
    }

    private static List<PathCandidate> singles(String prefix, String type) {
        List<PathCandidate> pool = new ArrayList<>();
        String[] grades = {"G11", "G12"};
        String[] difficulties = {"NB", "TH", "VD", "VDC"};
        for (int i = 0; i < 150; i++) {
            List<String> nodes = i < 30 ? List.of("X") : List.of();
            pool.add(new PathCandidate(prefix + i, 1, null, null, List.of(new CountingUnit(null, type,
                    grades[(i / 4) % 2], difficulties[i % 4], nodes, List.of(), null))));
        }
        return pool;
    }
}
