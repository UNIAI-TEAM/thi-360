package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-3 (tag {@code perf}): bài HSA mẫu — 150 câu, 3 phần, 2.000 ứng viên mỗi khối — phải giải dưới 10 s.
 * In thời gian đo ra System.out theo quy ước của lane.
 */
@Tag("perf")
class PaperSolverPerfTest {

    private static final String[] GRADES = {"G10", "G11", "G12"};
    private static final String[] DIFFS = {"NB", "TH", "VD"};

    @Test
    @DisplayName("HSA-like: 150 câu, 3 phần, 2.000 ứng viên/khối, giải < 10 s")
    void hsaLikeUnder10s() {
        List<BlockQuota> part1 = List.of(
                combinedBlock("P1-B1", 20),
                combinedBlock("P1-B2", 25));
        List<BlockQuota> part2 = List.of(
                combinedBlock("P2-B1", 35),
                combinedBlock("P2-B2", 30));
        // phần CHOICE 2 lựa chọn: 2 đường, mỗi đường 5 khối × tổng 150 câu
        List<SolvePart> parts = List.of(
                new SolvePart.Fixed("P1", part1),
                new SolvePart.Fixed("P2", part2),
                SolvePart.Choice.of("P3", List.of(
                        new SolveOption("KH1", List.of(combinedBlock("P3-B1", 20),
                                combinedBlock("P3-B2", 20))),
                        new SolveOption("KH2", List.of(combinedBlock("P3-B3", 20),
                                combinedBlock("P3-B4", 20))))));

        Random rng = new Random(261116L);
        PaperSolver solver = new PaperSolver();
        long t0 = System.nanoTime();
        SolveOutcome out = solver.solveAll(parts, block -> generatePool(rng, 2_000), SolveMode.check());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("PERF PaperSolver HSA ms=" + ms);

        assertThat(out.results()).hasSize(2);
        for (PathResult r : out.results()) {
            assertThat(r.status()).isEqualTo(SolveStatus.VALID);
            int picked = r.witness().stream().mapToInt(w -> w.picks().size()).sum();
            assertThat(picked).isEqualTo(150);
        }
        assertThat(ms).isLessThan(10_000);
    }

    @Test
    @DisplayName("Nhánh quay lui: khối chùm + năng lực trên nguồn lớn vẫn nhanh")
    void backtrackingBranchPerf() {
        // 1 khối chùm (10 bộ × 3 câu con) + 1 khối SEPARATE có chỉ tiêu năng lực, 800 ứng viên mỗi khối
        List<CellQuota> cells = List.of(
                new CellQuota("G12", "NB", 15),
                new CellQuota("G12", "TH", 10),
                new CellQuota("G12", "VD", 5));
        BlockQuota setBlock = BlockQuota.setBlock("SB", 10, 3, new MatrixQuota.Combined(cells));
        BlockQuota sepBlock = new BlockQuota("SP", 40, null, null,
                new MatrixQuota.Separate(
                        List.of(new DimensionQuota("G12", 40)),
                        List.of(new DimensionQuota("NB", 15), new DimensionQuota("TH", 15),
                                new DimensionQuota("VD", 10))),
                List.of(new CompetencyQuota("TUDUY", 40)), List.of(), null, null);

        Random rng = new Random(999L);
        List<List<PathCandidate>> pools = List.of(
                genSetPool(rng, 800, 3),
                genCompPool(rng, 800));
        SolvePath path = new SolvePath(0, List.of(), List.of(setBlock, sepBlock));

        PaperSolver solver = new PaperSolver();
        long t0 = System.nanoTime();
        PathResult r = solver.solve(path, pools, SolveMode.generate(7L));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("PERF PaperSolver backtracking ms=" + ms);
        assertThat(r.status()).isEqualTo(SolveStatus.VALID);
        assertThat(ms).isLessThan(10_000);
    }

    private BlockQuota combinedBlock(String id, int qc) {
        // 9 ô với chỉ tiêu gần đều, tổng đúng qc
        List<CellQuota> cells = new ArrayList<>();
        int base = qc / 9;
        int rem = qc % 9;
        int i = 0;
        for (String g : GRADES) {
            for (String d : DIFFS) {
                cells.add(new CellQuota(g, d, base + (i++ < rem ? 1 : 0)));
            }
        }
        return BlockQuota.combined(id, qc, cells);
    }

    private List<PathCandidate> generatePool(Random rng, int n) {
        List<PathCandidate> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String g = GRADES[rng.nextInt(GRADES.length)];
            String d = DIFFS[rng.nextInt(DIFFS.length)];
            out.add(new PathCandidate("q" + rng.nextInt(10_000_000) + "-" + i, 1, null, null,
                    List.of(new CountingUnit(null, g, d, List.of(), List.of(), BigDecimal.ONE))));
        }
        // bảo đảm mọi ô có ít nhất một vài ứng viên
        int k = 0;
        for (String g : GRADES) {
            for (String d : DIFFS) {
                for (int j = 0; j < 40; j++) {
                    out.add(new PathCandidate("fill-" + (k++) + "-" + i0(out), 1, null, null,
                            List.of(new CountingUnit(null, g, d, List.of(), List.of(), BigDecimal.ONE))));
                }
            }
        }
        return out;
    }

    private static int i0(List<PathCandidate> out) {
        return out.size();
    }

    private List<PathCandidate> genSetPool(Random rng, int n, int setSize) {
        List<PathCandidate> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<CountingUnit> units = new ArrayList<>(setSize);
            for (int u = 0; u < setSize; u++) {
                // các bộ gần cân: mỗi đơn vị ngẫu nhiên ô
                String g = "G12";
                String d = DIFFS[rng.nextInt(DIFFS.length)];
                units.add(new CountingUnit("s" + i + "-c" + u, g, d, List.of(), List.of(), null));
            }
            out.add(new PathCandidate("S" + i, 1, "S" + i, null, units));
        }
        return out;
    }

    private List<PathCandidate> genCompPool(Random rng, int n) {
        List<PathCandidate> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String d = DIFFS[rng.nextInt(DIFFS.length)];
            out.add(new PathCandidate("c" + i, 1, null, null,
                    List.of(new CountingUnit(null, "G12", d, List.of(), List.of("TUDUY"), null))));
        }
        return out;
    }
}
