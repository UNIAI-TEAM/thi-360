package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.solver.CandidateSource;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PaperSolver;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveReason;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.exam.domain.solver.SubjectQuota;
import vn.unicom.thi360.exam.domain.solver.TopicQuota;

class PreviewPathSolverTest {
    @Test
    void choiceIsSolvedAtFullCountBeforeRemovingOne() {
        GroupQuota a = GroupQuota.single("a", 2);
        GroupQuota b = GroupQuota.single("b", 2);
        SectionOnPath first = new SectionOnPath("A", "a", null, null, List.of(a), "NATURAL");
        SectionOnPath second = new SectionOnPath("B", "b", null, null, List.of(b), "NATURAL");
        SubjectChoice choice = new SubjectChoice("choice", List.of(first, second), 2, List.of(2, 1));
        SubjectPick pick = new SubjectPick("choice", List.of(first, second), List.of(2, 1));
        SolvePath path = new SolvePath(0, List.of(), List.of(), List.of(choice), List.of(List.of(pick)));
        var pools = Map.of("a", List.of(question("a1"), question("a2")),
                "b", List.of(question("b1"), question("b2")));

        var result = PreviewPathSolver.solve(new PaperSolver(), path, group -> pools.get(group.groupId()), 7);
        assertThat(result.path().status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.dropped()).hasSize(1);
        assertThat(result.path().witness()).extracting(w -> w.resolvedCount()).containsExactlyInAnyOrder(2, 1);
        assertThat(result.path().witness().stream().flatMap(w -> w.groups().stream())
                .flatMap(group -> group.picks().stream())).hasSize(3)
                .doesNotContain(result.dropped().getFirst());
    }

    @Test
    void singleChoiceSectionSolvesSeventeenThenDropsOne() {
        GroupQuota group = GroupQuota.single("physics", 17);
        SectionOnPath section = new SectionOnPath("PHYSICS", "physics", 17, null, List.of(group), "NATURAL");
        List<PathCandidate> candidates = java.util.stream.IntStream.range(0, 17)
                .mapToObj(index -> question("question-" + index)).toList();
        var result = PreviewPathSolver.solveSection(new PaperSolver(), SolvePath.of(0, List.of(section)),
                ignored -> candidates, 42, true);

        assertThat(result.path().status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.path().witness().getFirst().resolvedCount()).isEqualTo(16);
        assertThat(result.path().witness().getFirst().groups().getFirst().picks()).hasSize(16)
                .doesNotContain(result.dropped().getFirst());
        assertThat(result.dropped()).hasSize(1);
    }

    // ---------------------------------------------------------------- R3-1 / R3-2: retries of the section trim

    @Test
    void aLaterWitnessTrimsWhenTheFirstOneIsBlockedByTheMatrixMinimum() {
        // Attempt 0 draws the set whose second child is the only slack: every single question is needed by a
        // minimum and the set cannot be split, so the trim is blocked. Attempt 1 draws a set with one V child more
        // than needed, so one z may go and the minimum of V (7) still holds.
        var fixture = new SlackFixture(attempt -> attempt == 0 ? List.of(SlackFixture.TIGHT) : List.of(SlackFixture.LOOSE));

        var result = PreviewPathSolver.solveSection(new PaperSolver(), fixture.path(), fixture, 11, true);

        assertThat(result.path().status()).isEqualTo(SolveStatus.VALID);
        assertThat(fixture.solves()).as("a second witness was solved after the blocked first one").isEqualTo(2);
        var witness = result.path().witness().getFirst();
        assertThat(witness.resolvedCount()).isEqualTo(16);
        assertThat(result.dropped()).hasSize(1);
        assertThat(result.dropped().getFirst().questionId()).startsWith("z");
        List<QuestionPick> kept = witness.groups().stream().flatMap(group -> group.picks().stream()).toList();
        assertThat(kept).hasSize(15).doesNotContain(result.dropped().getFirst());
        assertThat(fixture.unitsOf(kept, "V", 1)).as("minimum of V after the trim").isGreaterThanOrEqualTo(7);
        assertThat(fixture.unitsOf(kept, "X", 1)).as("minimum of X after the trim").isEqualTo(9);
    }

    @Test
    void theBlockedRetriesShareOneBudgetInsteadOfStartingAFreshOneEachTime() {
        // Every witness is blocked, so the trim retries; each full solve takes 25 simulated seconds against a 30
        // second budget. A fresh budget per retry would run four solves (100 s) and then answer a plain INVALID.
        var clock = new SteppingClock();
        var fixture = new SlackFixture(attempt -> List.of(SlackFixture.TIGHT));
        fixture.onSolve(() -> clock.advance(Duration.ofSeconds(25)));
        var solver = new PaperSolver(new SolverLimits(200, 20_000, Duration.ofSeconds(30), 8), clock);

        var result = PreviewPathSolver.solveSection(solver, fixture.path(), fixture, 11, true);

        assertThat(result.path().status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.path().reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
        assertThat(result.dropped()).isEmpty();
        assertThat(fixture.solves()).as("no new solve starts once the budget is spent").isEqualTo(2);
        assertThat(clock.elapsed()).as("simulated time").isLessThan(Duration.ofSeconds(60));
    }

    /**
     * One section of 17 units: g1 nine X singles, g2 six of seven V singles, g3 one set of two children. The minimums
     * are X 9 (g1) and V 7 (g2, g3), so one unit of the 17 is slack. The pool of g3 is chosen per attempt: a solve
     * reads g1 once and the trim that follows reads it once more, so the attempt is the number of g1 reads / 2.
     */
    private static final class SlackFixture implements CandidateSource {
        static final PathCandidate TIGHT = set("tight", new String[] {"V"}, new String[] {});
        static final PathCandidate LOOSE = set("loose", new String[] {"V"}, new String[] {"V"});
        private final List<PathCandidate> x = new ArrayList<>();
        private final List<PathCandidate> z = new ArrayList<>();
        private final IntFunction<List<PathCandidate>> sets;
        private int g1Reads;
        private Runnable onSolve = () -> {};

        SlackFixture(IntFunction<List<PathCandidate>> sets) {
            this.sets = sets;
            for (int i = 0; i < 9; i++) x.add(single("x" + i, "X"));
            for (int i = 0; i < 7; i++) z.add(single("z" + i, "V"));
        }

        void onSolve(Runnable action) {
            onSolve = action;
        }

        /** Solves started so far: the solver reads g1 once per solve, the trim once per VALID solve. */
        int solves() {
            return (g1Reads + 1) / 2;
        }

        SolvePath path() {
            GroupQuota g1 = new GroupQuota("g1", GroupQuota.GroupKind.SINGLE, 9, null, null, null, null,
                    null, List.of(), List.of(), null, null);
            GroupQuota g2 = new GroupQuota("g2", GroupQuota.GroupKind.SINGLE, 6, null, null, null, null,
                    null, List.of(), List.of(), null, null);
            GroupQuota g3 = new GroupQuota("g3", GroupQuota.GroupKind.SET, null, null, 1, 2, null,
                    null, List.of(), List.of(), null, null);
            SubjectQuota quota = new SubjectQuota(null, null, List.of(
                    new TopicQuota("qx", List.of("X"), 9, List.of(), List.of(), List.of("g1")),
                    new TopicQuota("qv", List.of("V"), 7, List.of(), List.of(), List.of("g2", "g3"))),
                    List.of(), List.of(), true);
            return SolvePath.of(0, List.of(new SectionOnPath("LY", "ly", 17, quota, List.of(g1, g2, g3), "NATURAL")));
        }

        @Override
        public List<PathCandidate> candidatesFor(GroupQuota group) {
            switch (group.groupId()) {
                case "g1" -> {
                    g1Reads++;
                    if (g1Reads % 2 == 1) onSolve.run();
                    return x;
                }
                case "g2" -> {
                    return z;
                }
                default -> {
                    return sets.apply(Math.max(g1Reads - 1, 0) / 2);
                }
            }
        }

        /** Units of the picks that carry {@code node}; {@code attempt} selects the g3 pool the picked set came from. */
        int unitsOf(List<QuestionPick> picks, String node, int attempt) {
            List<PathCandidate> all = new ArrayList<>(x);
            all.addAll(z);
            all.addAll(sets.apply(attempt));
            Map<String, PathCandidate> byId = new HashMap<>();
            all.forEach(candidate -> byId.put(candidate.questionId(), candidate));
            int count = 0;
            for (QuestionPick pick : picks) {
                for (CountingUnit unit : byId.get(pick.questionId()).units()) {
                    if (unit.mapidIds().contains(node)) count++;
                }
            }
            return count;
        }

        private static PathCandidate single(String id, String node) {
            return new PathCandidate(id, 1, null, null,
                    List.of(new CountingUnit(null, "SHORT_ANSWER", null, null, List.of(node), List.of(), null)));
        }

        private static PathCandidate set(String id, String[]... children) {
            List<CountingUnit> units = new ArrayList<>();
            for (int i = 0; i < children.length; i++) {
                units.add(new CountingUnit(id + "-c" + i, "SHORT_ANSWER", null, null, List.of(children[i]),
                        List.of(), null));
            }
            return new PathCandidate(id, 1, id, null, units);
        }
    }

    /** Clock that only moves when the test moves it. */
    private static final class SteppingClock extends Clock {
        private final Instant start = Instant.parse("2026-11-20T00:00:00Z");
        private Instant now = start;

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        Duration elapsed() {
            return Duration.between(start, now);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static PathCandidate question(String id) {
        return PathCandidate.standalone(id, 1, "SINGLE_CHOICE", null, null, null);
    }
}
