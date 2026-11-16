package vn.unicom.thi360.exam.domain.solver;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Dựng dữ liệu và đồng hồ cho test của {@link PaperSolver}.
 */
final class SolverTestFixtures {

    static final String G = "G1";
    static final String D = "D1";

    private SolverTestFixtures() {
    }

    static CountingUnit unit(String grade, String difficulty) {
        return unit(null, grade, difficulty);
    }

    static CountingUnit unit(String childId, String grade, String difficulty) {
        return new CountingUnit(childId, grade, difficulty, List.of(), List.of(), null);
    }

    static CountingUnit unit(String grade, String difficulty, List<String> competencies) {
        return new CountingUnit(null, grade, difficulty, List.of(), competencies, null);
    }

    /** Câu độc lập một đơn vị. */
    static PathCandidate cand(String id, String grade, String difficulty) {
        return new PathCandidate(id, 1, null, null, List.of(unit(grade, difficulty)));
    }

    static PathCandidate cand(String id, String grade, String difficulty, List<String> competencies) {
        return new PathCandidate(id, 1, null, null, List.of(unit(grade, difficulty, competencies)));
    }

    /** Bộ câu hỏi / Điền khuyết nguyên vẹn: {@code units} phần tử, setId = id. */
    static PathCandidate set(String id, CountingUnit... units) {
        return new PathCandidate(id, 1, id, null, List.of(units));
    }

    /** Bộ với các câu con cùng khối lớp/độ khó. */
    static PathCandidate setOf(String id, String[][] gradeDiffs) {
        CountingUnit[] units = new CountingUnit[gradeDiffs.length];
        for (int i = 0; i < units.length; i++) {
            units[i] = unit(id + "-c" + (i + 1), gradeDiffs[i][0], gradeDiffs[i][1]);
        }
        return set(id, units);
    }

    static BlockQuota block(String blockId, int questionCount, CellQuota... cells) {
        return BlockQuota.combined(blockId, questionCount, List.of(cells));
    }

    static CellQuota cell(String grade, String difficulty, int quota) {
        return new CellQuota(grade, difficulty, quota);
    }

    static DimensionQuota dim(String code, int quota) {
        return new DimensionQuota(code, quota);
    }

    @SafeVarargs
    static List<List<PathCandidate>> lists(List<PathCandidate>... lists) {
        return List.of(lists);
    }

    /** Đường một khối cố định. */
    static SolvePath path(BlockQuota... blocks) {
        return new SolvePath(0, List.of(), List.of(blocks));
    }

    /** Phần FIXED gói các khối (để dùng với solveAll một đường). */
    static List<SolvePart> parts(BlockQuota... blocks) {
        return List.of(new SolvePart.Fixed("P1", List.of(blocks)));
    }

    /**
     * Đồng hồ bước: {@code instant()} trả {@code base} cho {@code callsBeforeExpiry} lần gọi đầu, sau đó
     * nhảy vọt qua mọi hạn. Dùng để ép DEADLINE_EXCEEDED trong khi giải.
     */
    static final class SteppingClock extends Clock {
        private final Instant base;
        private final int callsBeforeExpiry;
        private int calls;

        SteppingClock(Instant base, int callsBeforeExpiry) {
            this.base = base;
            this.callsBeforeExpiry = callsBeforeExpiry;
        }

        @Override
        public Instant instant() {
            calls++;
            return calls <= callsBeforeExpiry ? base : base.plusSeconds(86_400);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        int calls() {
            return calls;
        }
    }
}
