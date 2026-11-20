package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Perf (tag {@code perf}, UNI-2758): Toán HSA of 50 questions (32 single choice, 15 short answer, one set of
 * three) with six MAPID rules: exact counts over the subject and over one group, a plain minimum and a cap that
 * most questions run into. 2.000 candidates per group, each carrying one to three of twelve nodes. The solver
 * must find the exact witness in every mode within the budget; prints {@code PERF MapidCaps ms=<n>}.
 */
@Tag("perf")
class MapidCapPerfTest {
    private static final int NODES = 12;

    @Test
    @DisplayName("HSA Toán 50 câu, 6 quy tắc MAPID, 2.000 ứng viên/nhóm < 10 s")
    void sixRulesOverFiftyQuestions() {
        List<TopicQuota> quotas = List.of(
                quota("minN0", "n0", 3), quota("minN1", "n1", 3), quota("minN2", "n2", 1), quota("minN3", "n3", 3));
        List<TopicCap> caps = List.of(
                new TopicCap("exactN0", null, List.of("n0"), 3),
                new TopicCap("exactN1InSa", "SA", List.of("n1"), 3),
                new TopicCap("exactN3", null, List.of("n3"), 3),
                new TopicCap("capN4", null, List.of("n4"), 2));
        SubjectQuota quota = new SubjectQuota(null, null, quotas, List.of(), caps);
        SectionOnPath math = new SectionOnPath("MATH", "SEC-MATH", 50, quota, List.of(
                new GroupQuota("SC", GroupQuota.GroupKind.SINGLE, 32, "SINGLE_CHOICE", null, null, null,
                        null, List.of(), List.of(), null, null),
                new GroupQuota("SA", GroupQuota.GroupKind.SINGLE, 15, "SHORT_ANSWER", null, null, null,
                        null, List.of(), List.of(), null, null),
                GroupQuota.set("SET", 1, 3, null)));
        Random random = new Random(2758);
        List<PathCandidate> sc = pool(random, "sc", "SINGLE_CHOICE", 2000);
        List<PathCandidate> sa = pool(random, "sa", "SHORT_ANSWER", 2000);
        List<PathCandidate> sets = sets(random, 2000);
        CandidateSource source = group -> switch (group.groupId()) {
            case "SC" -> sc;
            case "SA" -> sa;
            default -> sets;
        };
        PaperSolver solver = new PaperSolver();
        long started = System.nanoTime();
        for (SolveMode mode : List.of(SolveMode.check(), SolveMode.generate(1), SolveMode.generate(2),
                SolveMode.generate(3))) {
            PathResult result = solver.solveAll(List.of(SolverTestFixtures.part("P1", math)), source, mode)
                    .results().getFirst();
            assertThat(result.status()).as("%s", mode).isEqualTo(SolveStatus.VALID);
            assertThat(carriers(result, sc, sa, sets, "n0", null)).as("exact n0").isEqualTo(3);
            assertThat(carriers(result, sc, sa, sets, "n3", null)).as("exact n3").isEqualTo(3);
            assertThat(carriers(result, sc, sa, sets, "n1", "SA")).as("exact n1 in SA").isEqualTo(3);
            assertThat(carriers(result, sc, sa, sets, "n4", null)).as("cap n4").isLessThanOrEqualTo(2);
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        System.out.println("PERF MapidCaps ms=" + millis);
        assertThat(millis).isLessThan(10_000);
    }

    @Test
    @DisplayName("Quy tắc xung đột (tối thiểu 4, cận 3) được chứng minh bất khả thi và gọi tên cận < 10 s")
    void conflictingRulesFailFastAndNameTheCap() {
        SubjectQuota quota = new SubjectQuota(null, null, List.of(quota("minN0", "n0", 4)), List.of(),
                List.of(new TopicCap("exactN0", null, List.of("n0"), 3)));
        SectionOnPath math = new SectionOnPath("MATH", "SEC-MATH", 50, quota, List.of(
                new GroupQuota("SC", GroupQuota.GroupKind.SINGLE, 32, "SINGLE_CHOICE", null, null, null,
                        null, List.of(), List.of(), null, null),
                new GroupQuota("SA", GroupQuota.GroupKind.SINGLE, 15, "SHORT_ANSWER", null, null, null,
                        null, List.of(), List.of(), null, null),
                GroupQuota.set("SET", 1, 3, null)));
        Random random = new Random(2758);
        List<PathCandidate> sc = pool(random, "sc", "SINGLE_CHOICE", 2000);
        List<PathCandidate> sa = pool(random, "sa", "SHORT_ANSWER", 2000);
        List<PathCandidate> sets = sets(random, 2000);
        CandidateSource source = group -> switch (group.groupId()) {
            case "SC" -> sc;
            case "SA" -> sa;
            default -> sets;
        };
        long started = System.nanoTime();
        PathResult result = new PaperSolver().solveAll(List.of(SolverTestFixtures.part("P1", math)), source,
                SolveMode.check()).results().getFirst();
        long millis = (System.nanoTime() - started) / 1_000_000;
        System.out.println("PERF MapidCapsConflict ms=" + millis + " status=" + result.status()
                + " reason=" + result.reason() + " quota=" + result.failingQuota());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.failingQuota()).isNotNull();
        assertThat(result.failingQuota().key()).isEqualTo("exactN0");
        assertThat(millis).isLessThan(10_000);
    }

    private static int carriers(PathResult result, List<PathCandidate> sc, List<PathCandidate> sa,
            List<PathCandidate> sets, String node, String groupId) {
        int count = 0;
        for (GroupWitness group : result.witness().getFirst().groups()) {
            if (groupId != null && !groupId.equals(group.groupId())) continue;
            List<PathCandidate> pool = group.groupId().equals("SC") ? sc : group.groupId().equals("SA") ? sa : sets;
            for (QuestionPick pick : group.picks()) {
                PathCandidate candidate = pool.stream().filter(c -> c.questionId().equals(pick.questionId()))
                        .findFirst().orElseThrow();
                for (CountingUnit unit : candidate.units()) if (unit.mapidIds().contains(node)) count++;
            }
        }
        return count;
    }

    private static TopicQuota quota(String id, String node, int count) {
        return new TopicQuota(id, List.of(node), count, List.of(), List.of(), List.of());
    }

    private static List<String> nodes(Random random) {
        List<String> nodes = new ArrayList<>();
        int n = 1 + random.nextInt(3);
        for (int i = 0; i < n; i++) {
            String node = "n" + random.nextInt(NODES);
            if (!nodes.contains(node)) nodes.add(node);
        }
        return nodes;
    }

    private static List<PathCandidate> pool(Random random, String prefix, String type, int size) {
        List<PathCandidate> pool = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            pool.add(new PathCandidate(prefix + i, 1, null, null, List.of(
                    new CountingUnit(null, type, null, null, nodes(random), List.of(), null))));
        }
        return pool;
    }

    private static List<PathCandidate> sets(Random random, int size) {
        List<PathCandidate> pool = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            List<CountingUnit> units = new ArrayList<>();
            for (int child = 0; child < 3; child++) {
                units.add(new CountingUnit("set" + i + "c" + child, "SHORT_ANSWER", null, null, nodes(random),
                        List.of(), null));
            }
            pool.add(new PathCandidate("set" + i, 1, "set" + i, null, units));
        }
        return pool;
    }
}
