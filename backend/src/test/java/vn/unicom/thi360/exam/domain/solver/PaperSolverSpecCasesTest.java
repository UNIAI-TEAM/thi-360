package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.D;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.G;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.block;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.cand;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.cell;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.lists;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.path;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.set;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.setOf;
import static vn.unicom.thi360.exam.domain.solver.SolverTestFixtures.unit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Các ca bắt buộc của spec mục 10 cho {@link PaperSolver}.
 */
class PaperSolverSpecCasesTest {

    private final PaperSolver solver = new PaperSolver();

    @Test
    @DisplayName("Hai khối cùng chỉ có q1 làm nguồn: từng khối đủ nhưng cả đường INVALID")
    void sharedSourceInvalid() {
        BlockQuota b1 = block("B1", 1, cell(G, D, 1));
        BlockQuota b2 = block("B2", 1, cell(G, D, 1));
        PathCandidate q1 = cand("q1", G, D);

        assertThat(solver.solve(path(b1), lists(List.of(q1)), SolveMode.check()).status())
                .isEqualTo(SolveStatus.VALID);
        assertThat(solver.solve(path(b2), lists(List.of(q1)), SolveMode.check()).status())
                .isEqualTo(SolveStatus.VALID);

        PathResult both = solver.solve(path(b1, b2), lists(List.of(q1), List.of(q1)), SolveMode.check());
        assertThat(both.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(both.reason()).isEqualTo(SolveReason.NO_JOINT_SOLUTION);
    }

    @Test
    @DisplayName("Năng lực thiếu nguồn rõ ràng: INVALID (INSUFFICIENT_SOURCE)")
    void competencyShortSource() {
        BlockQuota b = new BlockQuota("B", 2, null, null,
                new MatrixQuota.Combined(List.of(cell("NB", D, 1), cell("TH", D, 1))),
                List.of(new CompetencyQuota("TUDUY", 1), new CompetencyQuota("GQT", 1)),
                List.of(), null, null);
        List<PathCandidate> cands = List.of(
                cand("q1", "NB", D, List.of("TUDUY")),
                cand("q2", "TH", D, List.of("TUDUY")));
        PathResult r = solver.solve(path(b), lists(cands), SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(r.reason()).isEqualTo(SolveReason.INSUFFICIENT_SOURCE);
    }

    @Test
    @DisplayName("Năng lực đủ tổng nhưng không khớp ô: INVALID (NO_JOINT_SOLUTION)")
    void competencyTotalsOkCellsWrong() {
        // TUDUY và GQT chỉ nằm trên câu NB; chỉ tiêu ô NB=1, TH=1 → không phủ được cả hai năng lực
        BlockQuota b = new BlockQuota("B", 2, null, null,
                new MatrixQuota.Combined(List.of(cell("NB", D, 1), cell("TH", D, 1))),
                List.of(new CompetencyQuota("TUDUY", 1), new CompetencyQuota("GQT", 1)),
                List.of(), null, null);
        List<PathCandidate> cands = List.of(
                cand("q1", "NB", D, List.of("TUDUY")),
                cand("q2", "NB", D, List.of("GQT")),
                cand("q3", "TH", D));
        PathResult r = solver.solve(path(b), lists(cands), SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(r.reason()).isEqualTo(SolveReason.NO_JOINT_SOLUTION);
    }

    @Test
    @DisplayName("Ô đủ số đếm nhưng không ghép được bộ nguyên vẹn (cần 3 NB + 2 TH, chỉ có bộ 4 NB + 1 TH)")
    void setCannotBePacked() {
        BlockQuota b = BlockQuota.setBlock("B", 1, 5,
                new MatrixQuota.Combined(List.of(cell("NB", D, 3), cell("TH", D, 2))));
        PathCandidate s1 = setOf("S1", new String[][]{
                {"NB", D}, {"NB", D}, {"NB", D}, {"NB", D}, {"TH", D}});
        PathCandidate s2 = setOf("S2", new String[][]{
                {"NB", D}, {"NB", D}, {"NB", D}, {"NB", D}, {"TH", D}});
        // hai bộ giống nhau: nguồn ô đủ (NB 8 >= 3, TH 2 >= 2) nhưng mỗi bộ đều vượt quota NB
        PathResult r = solver.solve(path(b), lists(List.of(s1, s2)), SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(r.reason()).isEqualTo(SolveReason.NO_JOINT_SOLUTION);
    }
    @Test
    @DisplayName("Bộ đúng chỉ tiêu thì ghép được và lấy nguyên vẹn")
    void setPackedWhole() {
        BlockQuota b = BlockQuota.setBlock("B", 1, 5,
                new MatrixQuota.Combined(List.of(cell("NB", D, 3), cell("TH", D, 2))));
        List<PathCandidate> cands = List.of(
                setOf("S1", new String[][]{{"NB", D}, {"NB", D}, {"TH", D}}), // setSize 3: loại
                setOf("S2", new String[][]{
                        {"NB", D}, {"NB", D}, {"NB", D}, {"TH", D}, {"TH", D}}),
                cand("qS", "NB", D));
        PathResult r = solver.solve(path(b), lists(cands), SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.VALID);
        assertThat(r.witness()).hasSize(1);
        assertThat(r.witness().get(0).picks())
                .extracting(QuestionPick::questionId).containsExactly("S2");
    }

    @Test
    @DisplayName("Điền khuyết lấy nguyên vẹn: chỉ tiêu khớp đúng số vị trí")
    void clozeTakenWhole() {
        BlockQuota b = block("B", 3, cell(G, D, 3));
        PathCandidate cloze = set("C1", unit(null, G, D), unit(null, G, D), unit(null, G, D));
        PathResult ok = solver.solve(path(b), lists(List.of(cloze)), SolveMode.check());
        assertThat(ok.status()).isEqualTo(SolveStatus.VALID);
        assertThat(ok.witness().get(0).picks()).extracting(QuestionPick::questionId)
                .containsExactly("C1");

        // chỉ tiêu 2 < số vị trí: câu Điền khuyết không thể được chọn một phần
        BlockQuota tooSmall = block("B2", 2, cell(G, D, 2));
        PathResult bad = solver.solve(path(tooSmall), lists(List.of(cloze)), SolveMode.check());
        assertThat(bad.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(bad.reason()).isEqualTo(SolveReason.NO_JOINT_SOLUTION);
    }

    @Test
    @DisplayName("Câu con vừa trong bộ vừa có bản độc lập: cả đường INVALID")
    void childInSetAndStandaloneConflicts() {
        // ch1 là câu con của bộ S và đồng thời có bản độc lập qCh1 trên đường
        BlockQuota b1 = BlockQuota.setBlock("B1", 1, 2,
                new MatrixQuota.Combined(List.of(cell(G, D, 2))));
        BlockQuota b2 = block("B2", 1, cell(G, D, 1));
        PathCandidate s = set("S", unit("ch1", G, D), unit("ch2", G, D));
        PathCandidate standalone = new PathCandidate("qCh1", 1, null, null,
                List.of(unit("ch1", G, D)));
        PathResult r = solver.solve(path(b1, b2), lists(List.of(s), List.of(standalone)),
                SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(r.reason()).isEqualTo(SolveReason.NO_JOINT_SOLUTION);
    }

    @Test
    @DisplayName("Hết hạn trong khi giải: UNCONFIRMED (DEADLINE_EXCEEDED)")
    void deadlineExceeded() {
        // bước nhảy sau lần đọc đồng hồ thứ ba: qua chuẩn bị, chết trong tìm kiếm
        SolverTestFixtures.SteppingClock clock = new SolverTestFixtures.SteppingClock(
                Instant.parse("2026-11-16T00:00:00Z"), 2);
        PaperSolver timed = new PaperSolver(
                new SolverLimits(200, 20_000, Duration.ofSeconds(30)), clock);
        List<PathCandidate> cands = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            cands.add(cand("q" + i, G, D));
        }
        // quota-0 bắt quay lui duyệt nhiều nhánh (không vào nhánh luồng)
        BlockQuota b = new BlockQuota("B", 1, null, null,
                new MatrixQuota.Combined(List.of(cell(G, D, 1))),
                List.of(new CompetencyQuota("X", 0)), List.of(), null, null);
        PathResult r = timed.solve(path(b), lists(cands), SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(r.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("Vượt 200 đường: UNCONFIRMED (PATH_LIMIT_EXCEEDED), không đường nào được giải")
    void pathLimitExceeded() {
        // hai phần CHOICE 15 × 14 = 210 đường > 200
        BlockQuota b = block("B", 1, cell(G, D, 1));
        List<SolveOption> o1 = new ArrayList<>();
        List<SolveOption> o2 = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            o1.add(new SolveOption("A" + i, List.of(b)));
        }
        for (int i = 0; i < 14; i++) {
            o2.add(new SolveOption("C" + i, List.of(b)));
        }
        List<SolvePart> parts = List.of(
                SolvePart.Choice.of("P1", o1), SolvePart.Choice.of("P2", o2));
        int[] fetchCount = {0};
        SolveOutcome out = solver.solveAll(parts, blk -> {
            fetchCount[0]++;
            return List.of(cand("q" + fetchCount[0], G, D));
        }, SolveMode.check());
        assertThat(out.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(out.reason()).isEqualTo(SolveReason.PATH_LIMIT_EXCEEDED);
        assertThat(out.results()).isEmpty();
        assertThat(fetchCount[0]).isZero();
    }

    @Test
    @DisplayName("Vượt 20.000 ứng viên một khối: UNCONFIRMED (CANDIDATE_LIMIT_EXCEEDED, kèm blockId)")
    void candidateLimitExceeded() {
        BlockQuota b = block("B", 1, cell(G, D, 1));
        List<PathCandidate> cands = new ArrayList<>(20_001);
        for (int i = 0; i < 20_001; i++) {
            cands.add(cand("q" + i, G, D));
        }
        PathResult r = solver.solve(path(b), lists(cands), SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(r.reason()).isEqualTo(SolveReason.CANDIDATE_LIMIT_EXCEEDED);
        assertThat(r.blockId()).isEqualTo("B");
    }

    @Test
    @DisplayName("Cùng hạt giống ra cùng đề; hạt giống khác cho đề khác; CHECK luôn cố định")
    void seededDeterminism() {
        BlockQuota b = block("B", 3, cell(G, D, 3));
        List<PathCandidate> cands = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            cands.add(cand("q" + i, G, D));
        }
        SolvePath p = path(b);

        PathResult g1a = solver.solve(p, lists(cands), SolveMode.generate(42));
        PathResult g1b = solver.solve(p, lists(cands), SolveMode.generate(42));
        assertThat(g1a.witness()).isEqualTo(g1b.witness());

        List<QuestionPick> baseline = g1a.witness().get(0).picks();
        boolean differ = false;
        for (long seed = 1; seed <= 5 && !differ; seed++) {
            differ = !solver.solve(p, lists(cands), SolveMode.generate(seed))
                    .witness().get(0).picks().equals(baseline);
        }
        assertThat(differ).as("some seed must yield a different witness").isTrue();

        PathResult c1 = solver.solve(p, lists(cands), SolveMode.check());
        PathResult c2 = solver.solve(p, lists(cands), SolveMode.check());
        assertThat(c1.witness()).isEqualTo(c2.witness());
        assertThat(c1.witness().get(0).picks()).extracting(QuestionPick::questionId)
                .containsExactly("q0", "q1", "q2");
    }

    @Test
    @DisplayName("Liệt kê đường: FIXED + CHOICE có phần chung; mỗi đường ghi lựa chọn đã chọn")
    void pathEnumeration() {
        BlockQuota fixedBlock = block("BF", 1, cell(G, D, 1));
        BlockQuota commonBlock = block("BC", 1, cell(G, D, 1));
        List<SolvePart> parts = List.of(
                new SolvePart.Fixed("PF", List.of(fixedBlock)),
                new SolvePart.Choice("PC", List.of(commonBlock), List.of(
                        new SolveOption("EN", List.of(block("BO1", 1, cell(G, D, 1)))),
                        new SolveOption("KH1", List.of(block("BO2", 1, cell(G, D, 1)))))));
        List<PathCandidate> pool = List.of(
                cand("qa", G, D), cand("qb", G, D), cand("qc", G, D));
        Map<String, List<PathCandidate>> candidates = Map.of(
                "BF", pool, "BC", pool, "BO1", pool, "BO2", pool);
        SolveOutcome out = solver.solveAll(parts, candidates, SolveMode.check());
        // 3 khối trên đường, 3 câu dùng chung qa/qb/qc → đủ cho từng khối
        assertThat(out.status()).isEqualTo(SolveStatus.VALID);
        assertThat(out.results()).hasSize(2);
        for (PathResult r : out.results()) {
            assertThat(r.status()).isEqualTo(SolveStatus.VALID);
        }
        assertThat(out.results().get(0).path().chosenOptions())
                .containsExactly(new ChosenOption("PC", "EN"));
        assertThat(out.results().get(1).path().chosenOptions())
                .containsExactly(new ChosenOption("PC", "KH1"));
    }

    @Test
    @DisplayName("R1-1: ứng viên không usable không được nối cầu khóa giữa hai câu hợp lệ")
    void unusableBridgeDoesNotMergeKeys() {
        // U không usable ở cả hai khối nhưng mang hai khóa: "U" (id bộ) và "q" (câu con).
        // q dùng được ở B1, U dùng được ở B2; nếu DSU gộp khóa qua U-thiếu-giá-trị thì q và U bị coi là
        // trùng khóa → NO_JOINT_SOLUTION dù {q, U} là một đề hợp lệ.
        BlockQuota b1 = block("B1", 1, cell(G, D, 1));
        BlockQuota b2 = block("B2", 1, cell("G2", D, 1));
        PathCandidate bridge = set("U", unit("q", G, D), unit("u2", "G2", D));
        PathCandidate q = cand("q", G, D);
        PathCandidate u = new PathCandidate("U", 1, null, null, List.of(unit(null, "G2", D)));
        PathResult r = solver.solve(path(b1, b2),
                lists(List.of(bridge, q), List.of(bridge, u)), SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.VALID);
        assertThat(r.witness().get(0).picks()).extracting(QuestionPick::questionId)
                .containsExactly("q");
        assertThat(r.witness().get(1).picks()).extracting(QuestionPick::questionId)
                .containsExactly("U");
    }

    @Test
    @DisplayName("R1-2: khối có questionCount > 0 nhưng không có chỉ tiêu dương nào → INVALID")
    void positiveCountWithoutQuotaInvalid() {
        BlockQuota empty = BlockQuota.combined("B", 1, List.of());
        PathResult r = solver.solve(path(empty),
                lists(List.of(cand("q1", G, D))), SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(r.reason()).isEqualTo(SolveReason.INSUFFICIENT_SOURCE);
    }

    @Test
    @DisplayName("R1-3: hai đường dùng cùng blockId nhưng chỉ tiêu khác nhau lấy đúng nguồn của mình")
    void sameBlockIdDifferentQuotaGetsOwnSource() {
        BlockQuota g1 = block("B", 1, cell("G1", D, 1));
        BlockQuota g2 = block("B", 1, cell("G2", D, 1));
        List<SolvePart> parts = List.of(SolvePart.Choice.of("PC", List.of(
                new SolveOption("O1", List.of(g1)),
                new SolveOption("O2", List.of(g2)))));
        SolveOutcome out = solver.solveAll(parts, b -> {
            if (b.equals(g1)) {
                return List.of(cand("q1", "G1", D));
            }
            if (b.equals(g2)) {
                return List.of(cand("q2", "G2", D));
            }
            return List.of();
        }, SolveMode.check());
        assertThat(out.status()).isEqualTo(SolveStatus.VALID);
        assertThat(out.results()).hasSize(2);
        for (PathResult r : out.results()) {
            assertThat(r.status()).isEqualTo(SolveStatus.VALID);
        }
    }

    @Test
    @DisplayName("R1-4: hết hạn ngay trong vòng chuẩn bị của khối lớn → UNCONFIRMED (DEADLINE_EXCEEDED)")
    void deadlineDuringLargePreparation() {
        // một khối với 20.000 ứng viên; đồng hồ bước hết hạn sau vài lần đọc — vòng lọc của Prep
        // phải tự kiểm hạn thay vì chỉ chờ đến khi dựng xong BlockContext.
        SolverTestFixtures.SteppingClock clock = new SolverTestFixtures.SteppingClock(
                Instant.parse("2026-11-16T00:00:00Z"), 3);
        PaperSolver timed = new PaperSolver(
                new SolverLimits(200, 20_000, Duration.ofSeconds(30)), clock);
        BlockQuota b = block("B", 1, cell(G, D, 1));
        List<PathCandidate> cands = new ArrayList<>(20_000);
        for (int i = 0; i < 20_000; i++) {
            cands.add(cand("q" + i, G, D));
        }
        PathResult r = timed.solve(path(b), lists(cands), SolveMode.check());
        assertThat(r.status()).isEqualTo(SolveStatus.UNCONFIRMED);
        assertThat(r.reason()).isEqualTo(SolveReason.DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("R1-7: một đường giải được, một đường không → tổng hợp INVALID, kết quả từng đường giữ nguyên")
    void solveAllMixedAggregation() {
        BlockQuota ok = block("BOK", 1, cell(G, D, 1));
        BlockQuota bad = block("BBAD", 1, cell("G9", D, 1));
        List<SolvePart> parts = List.of(SolvePart.Choice.of("PC", List.of(
                new SolveOption("O1", List.of(ok)),
                new SolveOption("O2", List.of(bad)))));
        SolveOutcome out = solver.solveAll(parts, b -> List.of(cand("q1", G, D)),
                SolveMode.check());
        assertThat(out.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(out.results()).hasSize(2);
        assertThat(out.results().get(0).status()).isEqualTo(SolveStatus.VALID);
        assertThat(out.results().get(1).status()).isEqualTo(SolveStatus.INVALID);
    }
}
