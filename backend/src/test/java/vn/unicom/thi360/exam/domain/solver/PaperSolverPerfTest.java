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
 * Hiệu năng (tag {@code perf}, plan D/BE-5a): bài HSA mẫu — 150 câu, 3 phần; Toán có chỉ tiêu cấp
 * môn của B9 và chùm; phần 3 là SUBJECT_CHOICE 5 môn chọn 3 cộng nhánh Tiếng Anh — 2.000 ứng viên
 * mỗi nhóm, giải cả 11 đường (10 cách chọn môn + 1 đường Tiếng Anh) dưới 10 giây. In thời gian đo
 * theo quy ước {@code PERF PaperSolver HSA11 ms=<n>}.
 */
@Tag("perf")
class PaperSolverPerfTest {

    private static final String[] GRADES = {SolverTestFixtures.G10, SolverTestFixtures.G11,
            SolverTestFixtures.G12};
    private static final String[] DIFFS = {SolverTestFixtures.NB, SolverTestFixtures.TH,
            SolverTestFixtures.VD};
    private static final String[] SCIENCE = {"PHYS", "CHEM", "BIO", "GEO", "TECH"};

    @Test
    @DisplayName("HSA: 150 câu, 3 phần, SUBJECT_CHOICE 5 chọn 3, 2.000 ứng viên/nhóm, 11 đường < 10 s")
    void hsaAllElevenPaths() {
        // Phần 1 — Toán 50 câu: chỉ tiêu cấp môn 35 SINGLE_CHOICE + 15 SHORT_ANSWER (B9),
        // ma trận lớp/độ khó của môn, các nhóm gồm một nhóm chùm lẫn dạng.
        SubjectQuota mathQuota = new SubjectQuota(
                SolverTestFixtures.answerQuotas(
                        SolverTestFixtures.dim(SolverTestFixtures.SC, 35),
                        SolverTestFixtures.dim(SolverTestFixtures.SA, 15)),
                new MatrixQuota.Separate(
                        List.of(SolverTestFixtures.dim(SolverTestFixtures.G10, 3),
                                SolverTestFixtures.dim(SolverTestFixtures.G11, 20),
                                SolverTestFixtures.dim(SolverTestFixtures.G12, 27)),
                        List.of(SolverTestFixtures.dim(SolverTestFixtures.NB, 2),
                                SolverTestFixtures.dim(SolverTestFixtures.TH, 21),
                                SolverTestFixtures.dim(SolverTestFixtures.VD, 27))));
        SectionOnPath math = new SectionOnPath("MATH", "SEC-MATH", 50, mathQuota, List.of(
                new GroupQuota("M-SC", GroupQuota.GroupKind.SINGLE, null, null, null, null, null,
                        null, List.of(), List.of(), null, null), // nhóm lấy phần còn lại
                GroupQuota.set("M-SET", 2, 5, null)));

        // Phần 2 — Ngữ văn 50 câu, không chỉ tiêu cấp môn.
        SectionOnPath lit = SolverTestFixtures.section("LIT", 50,
                SolverTestFixtures.group("L-G1", 50));

        // Phần 3 — SUBJECT_CHOICE 5 môn chọn 3, countsByPick 17-17-16 (D25) + nhánh Tiếng Anh 10 câu.
        List<SectionOnPath> scienceSections = new ArrayList<>();
        for (String code : SCIENCE) {
            scienceSections.add(new SectionOnPath(code, "SEC-" + code, null,
                    new SubjectQuota(null,
                            new MatrixQuota.Separate(
                                    List.of(SolverTestFixtures.dim(SolverTestFixtures.G11, 34),
                                            SolverTestFixtures.dim(SolverTestFixtures.G12, 66)),
                                    List.of(SolverTestFixtures.dim(SolverTestFixtures.NB, 30),
                                            SolverTestFixtures.dim(SolverTestFixtures.TH, 40),
                                            SolverTestFixtures.dim(SolverTestFixtures.VD, 30)),
                                    QuotaUnit.PERCENT)),
                    List.of(SolverTestFixtures.group(code + "-G1", 8),
                            GroupQuota.remainder(code + "-REM"))));
        }
        SubjectChoice science = new SubjectChoice("OPT-SCI", scienceSections, 3,
                List.of(17, 17, 16));
        SectionOnPath eng = SolverTestFixtures.section("ENG", 10,
                SolverTestFixtures.group("E-G1", 10));

        List<SolvePart> parts = List.of(
                SolverTestFixtures.part("P1", math),
                SolverTestFixtures.part("P2", lit),
                SolvePart.Choice.of("P3", List.of(science, new SolveOption.Sections("OPT-ENG",
                        List.of(eng)))));

        Random rng = new Random(261116L);
        CandidateSource source = group -> generatePool(rng, 2_000, group);
        PaperSolver solver = new PaperSolver();
        long t0 = System.nanoTime();
        SolveOutcome out = solver.solveAll(parts, source, SolveMode.check());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("PERF PaperSolver HSA11 ms=" + ms);

        assertThat(out.results()).hasSize(2); // đường Khoa học (10 cách chọn) + đường Tiếng Anh
        assertThat(out.stats().pathsEnumerated()).isEqualTo(11);
        assertThat(out.stats().subproblemsSolved()).isLessThanOrEqualTo(10);
        assertThat(ms).isLessThan(10_000);
    }

    private List<PathCandidate> generatePool(Random rng, int n, GroupQuota group) {
        List<PathCandidate> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            boolean isSet = group.kind() == GroupQuota.GroupKind.SET;
            String grade = GRADES[rng.nextInt(GRADES.length)];
            String diff = DIFFS[rng.nextInt(DIFFS.length)];
            String type = rng.nextBoolean() ? SolverTestFixtures.SC : SolverTestFixtures.SA;
            if (isSet) {
                int size = group.setSize();
                List<CountingUnit> units = new ArrayList<>(size);
                for (int k = 0; k < size; k++) {
                    units.add(SolverTestFixtures.unit("u" + k, type, grade, diff));
                }
                out.add(new PathCandidate("q" + i, 1, "s" + i, null, null, units));
            } else {
                out.add(new PathCandidate("q" + i, 1, null, null, null,
                        List.of(new CountingUnit(null, type, grade, diff, List.of(), List.of(),
                                BigDecimal.ONE))));
            }
        }
        return out;
    }
}
