package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hồi quy vòng 2 của reviewer trên giới hạn và hạn thời gian: giới hạn ứng viên là cạn nguồn
 * (UNCONFIRMED, R2-2/R2-11/R2-25), đường đã giải xong không bị đường hết hạn xóa (R2-7/R2-32),
 * và không truy vấn nguồn thêm khi đã hết hạn (R2-8).
 */
class SolverLimitDeadlineRegressionTest {

    private static PathCandidate cand(String id) {
        return PathCandidate.standalone(id, 1, "SINGLE_CHOICE", "G10", "NB", BigDecimal.ONE);
    }

    /** Đồng hồ có thể được nguồn đẩy qua hạn (mô phỏng truy vấn chậm). */
    private static final class AdvanceableClock extends Clock {
        private final Instant base = Instant.parse("2026-11-16T00:00:00Z");
        private Duration offset = Duration.ZERO;

        void advance(Duration d) {
            offset = offset.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return base.plus(offset);
        }
    }

    @Test
    @DisplayName("R2-2: nhóm vượt giới hạn ứng viên → UNCONFIRMED/CANDIDATE_LIMIT_EXCEEDED kèm groupId")
    void candidateLimitIsUnconfirmedWithGroup() {
        GroupQuota g = GroupQuota.single("ga", 1);
        SectionOnPath section = SolverTestFixtures.section("MATH", 1, g);
        PaperSolver solver = new PaperSolver(
                new SolverLimits(200, 2, Duration.ofSeconds(30), 8), Clock.systemUTC());
        PathResult result = solver.solvePath(SolverTestFixtures.path(section),
                x -> List.of(cand("q1"), cand("q2"), cand("q3")), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.CANDIDATE_LIMIT_EXCEEDED);
        assertThat(result.groupId()).isEqualTo("ga");
        assertThat(result.subjectCode()).isEqualTo("MATH");
    }

    @Test
    @DisplayName("R2-2/11: 20.001 ứng viên tại ranh giới 20.000 → UNCONFIRMED, không phải INVALID")
    void candidateLimitBoundaryTwentyThousandOne() {
        GroupQuota g = GroupQuota.single("ga", 1);
        SectionOnPath section = SolverTestFixtures.section("MATH", 1, g);
        List<PathCandidate> pool = new ArrayList<>(20_001);
        for (int i = 0; i < 20_001; i++) {
            pool.add(cand("q" + i));
        }
        PathResult result = new PaperSolver().solvePath(
                SolverTestFixtures.path(section), x -> pool, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(result.reason()).isEqualTo(SolveReason.CANDIDATE_LIMIT_EXCEEDED);
        assertThat(result.groupId()).isEqualTo("ga");
    }

    @Test
    @DisplayName("R2-7: đường INVALID đã xong trước, đường sau hết hạn → kết quả INVALID giữ nguyên")
    void deadlineKeepsCompletedInvalidResult() {
        // đường 0 không có nguồn → INVALID; đường 1: lần gọi nguồn đầu đẩy đồng hồ qua hạn
        GroupQuota gBad = GroupQuota.single("g-bad", 1);
        GroupQuota gSlow = GroupQuota.single("g-slow", 1);
        SectionOnPath secBad = new SectionOnPath("BAD", "s-bad", 1, SubjectQuota.NONE,
                List.of(gBad));
        SectionOnPath secSlow = new SectionOnPath("SLOW", "s-slow", 1, SubjectQuota.NONE,
                List.of(gSlow));
        SolvePart part = SolvePart.Choice.of("p1", List.of(
                new SolveOption.Sections("o-bad", List.of(secBad)),
                new SolveOption.Sections("o-slow", List.of(secSlow))));
        AdvanceableClock clock = new AdvanceableClock();
        PaperSolver solver = new PaperSolver(SolverLimits.defaults(), clock);
        CandidateSource source = g -> {
            if (g == gSlow) {
                clock.advance(Duration.ofDays(1));
                return List.of(cand("q1"));
            }
            return List.of();
        };
        SolveOutcome outcome = solver.solveAll(List.of(part), source, SolveMode.check());
        assertThat(outcome.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(outcome.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
        assertThat(outcome.results()).hasSize(1);
        assertThat(outcome.results().get(0).status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    @DisplayName("R2-8: lần gọi nguồn đầu vượt hạn → không gọi nguồn của nhóm còn lại")
    void noFurtherSourceCallAfterDeadline() {
        GroupQuota g1 = GroupQuota.single("g1", 1);
        GroupQuota g2 = GroupQuota.single("g2", 1);
        SectionOnPath section = new SectionOnPath("MATH", "s-math", 2, SubjectQuota.NONE,
                List.of(g1, g2));
        AdvanceableClock clock = new AdvanceableClock();
        AtomicInteger calls = new AtomicInteger();
        PaperSolver solver = new PaperSolver(SolverLimits.defaults(), clock);
        CandidateSource source = g -> {
            calls.incrementAndGet();
            clock.advance(Duration.ofDays(1));
            return List.of(cand("q1"));
        };
        PathResult result = solver.solvePath(SolverTestFixtures.path(section), source,
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(calls.get()).isEqualTo(1);
    }
}
