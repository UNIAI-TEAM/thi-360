package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * R2-22: each fixture makes one repaired inner scan exceed its 1024-step check interval.
 * The phase-specific clock expires on the first periodic check within the targeted scan;
 * earlier reads in the same phase remain before the deadline.
 */
class SolverInnerScanDeadlineTest {
    @Test
    void deadlineInterruptsLongMapidMatch() {
        GroupQuota group = new GroupQuota("g", GroupQuota.GroupKind.SINGLE, 1, null,
                null, null, null, null, List.of(),
                List.of(new MapidGroupQuota("scope", List.of("wanted"), 1)), null, null);
        List<String> nodes = IntStream.range(0, 2050).mapToObj(i -> "node-" + i).toList();
        CountingUnit unit = new CountingUnit(null, "SINGLE_CHOICE", "G10", "NB", nodes,
                List.of(), BigDecimal.ONE);
        PathCandidate candidate = new PathCandidate("q", 1, null, null, null, List.of(unit));
        ScanClock clock = new ScanClock();

        PathResult result = new PaperSolver(SolverLimits.defaults(), clock).solvePath(
                SolverTestFixtures.path(SolverTestFixtures.section("MATH", 1, group)),
                ignored -> List.of(candidate), SolveMode.check());

        assertThat(clock.scanReads).isEqualTo(2);
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
    }

    @Test
    void deadlineInterruptsSuffixGroupScan() {
        GroupQuota setGroup = GroupQuota.set("set", 1, 2, null);
        List<GroupQuota> groups = new java.util.ArrayList<>();
        groups.add(setGroup);
        IntStream.range(0, 1024).forEach(i -> groups.add(GroupQuota.single("empty-" + i, 0)));
        SectionOnPath section = new SectionOnPath("MATH", "section", 2, SubjectQuota.NONE, groups);
        PathCandidate candidate = new PathCandidate("set-question", 1, "set-question", null, null, List.of(
                new CountingUnit("a", "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(), BigDecimal.ONE),
                new CountingUnit("b", "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(), BigDecimal.ONE)));
        PairScanClock clock = new PairScanClock(6);

        PathResult result = new PaperSolver(SolverLimits.defaults(), clock).solvePath(
                SolverTestFixtures.path(section),
                g -> g == setGroup ? List.of(candidate) : List.of(), SolveMode.check());

        assertThat(clock.scanReads).isEqualTo(6);
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
    }

    @Test
    void deadlineInterruptsScanOfOtherSectionsDimensions() {
        GroupQuota group = GroupQuota.single("g", 1);
        List<SectionOnPath> sections = IntStream.range(0, 1025).mapToObj(i -> {
            SubjectQuota quota = new SubjectQuota(null, new MatrixQuota.Separate(
                    List.of(new DimensionQuota("G10", i == 0 ? 1 : 0)), List.of()));
            return new SectionOnPath("MATH", "section-" + i, i == 0 ? 1 : 0,
                    quota, i == 0 ? List.of(group) : List.of());
        }).toList();
        PathCandidate candidate = PathCandidate.standalone(
                "q", 1, "SINGLE_CHOICE", "G10", "NB", BigDecimal.ONE);
        UsabilityClock clock = new UsabilityClock(3);

        PathResult result = new PaperSolver(SolverLimits.defaults(), clock).solvePath(
                SolvePath.of(0, sections), ignored -> List.of(candidate), SolveMode.check());

        assertThat(clock.scanReads).isEqualTo(3);
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
    }

    @Test
    void deadlineInterruptsDimensionQuotaScan() {
        List<DimensionQuota> grades = IntStream.range(0, 2050)
                .mapToObj(i -> new DimensionQuota("grade-" + i, i == 2049 ? 1 : 0)).toList();
        SubjectQuota quota = new SubjectQuota(null, new MatrixQuota.Separate(grades, List.of()));
        GroupQuota group = GroupQuota.single("g", 1);
        PathCandidate candidate = PathCandidate.standalone(
                "q", 1, "SINGLE_CHOICE", "grade-2049", "NB", BigDecimal.ONE);
        UsabilityClock clock = new UsabilityClock(4);

        PathResult result = new PaperSolver(SolverLimits.defaults(), clock).solvePath(
                SolverTestFixtures.path(SolverTestFixtures.section("MATH", 1, quota, group)),
                ignored -> List.of(candidate), SolveMode.check());

        assertThat(clock.scanReads).isEqualTo(4);
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
    }

    private static final class UsabilityClock extends Clock {
        private final int expireAt;
        private int scanReads;

        private UsabilityClock(int expireAt) { this.expireAt = expireAt; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() {
            boolean matching = StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals(Prep.class.getName())
                            && frame.getMethodName().equals("usableFor")));
            if (matching && ++scanReads >= expireAt) {
                return Instant.EPOCH.plusSeconds(60);
            }
            return Instant.EPOCH;
        }
    }

    @Test
    void deadlineInterruptsCandidateKeyScan() {
        int size = 2050;
        GroupQuota group = GroupQuota.set("set-group", 1, size, null);
        List<CountingUnit> units = IntStream.range(0, size)
                .mapToObj(i -> new CountingUnit("child-" + i, "SINGLE_CHOICE", "G10", "NB",
                        List.of(), List.of(), BigDecimal.ONE))
                .toList();
        PathCandidate candidate = new PathCandidate("set", 1, "set", null, null, units);
        PairScanClock clock = new PairScanClock(4);

        PathResult result = new PaperSolver(SolverLimits.defaults(), clock).solvePath(
                SolverTestFixtures.path(SolverTestFixtures.section("MATH", size, group)),
                ignored -> List.of(candidate), SolveMode.check());

        assertThat(clock.scanReads).isEqualTo(4);
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
    }

    @Test
    void deadlineInterruptsSuffixRequirementScan() {
        List<CompetencyQuota> quotas = IntStream.range(0, 2050)
                .mapToObj(i -> new CompetencyQuota("competency-" + i, 0)).toList();
        GroupQuota group = new GroupQuota("set-group", GroupQuota.GroupKind.SET, null, null,
                1, 2, null, null, quotas, List.of(), null, null);
        PathCandidate candidate = new PathCandidate("set", 1, "set", null, null, List.of(
                new CountingUnit("a", "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(), BigDecimal.ONE),
                new CountingUnit("b", "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(), BigDecimal.ONE)));
        PairScanClock clock = new PairScanClock(5);

        PathResult result = new PaperSolver(SolverLimits.defaults(), clock).solvePath(
                SolverTestFixtures.path(SolverTestFixtures.section("MATH", 2, group)),
                ignored -> List.of(candidate), SolveMode.check());

        assertThat(clock.scanReads).isEqualTo(5);
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
    }

    private static final class PairScanClock extends Clock {
        private final int expireAt;
        private int scanReads;

        private PairScanClock(int expireAt) { this.expireAt = expireAt; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() {
            boolean matching = StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals(PathSearch.class.getName())
                            && frame.getMethodName().equals("buildPairs")));
            if (matching && ++scanReads >= expireAt) {
                return Instant.EPOCH.plusSeconds(60);
            }
            return Instant.EPOCH;
        }
    }

    private static final class ScanClock extends Clock {
        private int scanReads;

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() {
            boolean matching = StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals(Prep.Requirement.class.getName())
                            && frame.getMethodName().contains("mapidGroup")));
            if (matching && ++scanReads == 2) {
                return Instant.EPOCH.plusSeconds(60);
            }
            return Instant.EPOCH;
        }
    }
}
