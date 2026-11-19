package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
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
    private static final String[] SCIENCE = {"PHYS", "CHEM", "BIO", "HIST", "GEO"};

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

        // Phần 3 — 10 bộ ba; kiểm tra giải mỗi môn ở 17 câu (2B 6.3/H3), 12 phân bổ giấy chỉ cho
        // sinh đề; Tiếng Anh cũng đủ 50 câu.
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
                            GroupQuota.remainder(code + "-REM")),
                    List.of("PHYS", "CHEM", "BIO").contains(code) ? "NATURAL" : "SOCIAL"));
        }
        SubjectChoice science = new SubjectChoice("OPT-SCI", scienceSections, 3,
                List.of(17, 17, 16));
        SectionOnPath eng = SolverTestFixtures.section("ENG", 50,
                SolverTestFixtures.group("E-G1", 50));

        List<SolvePart> parts = List.of(
                SolverTestFixtures.part("P1", math),
                SolverTestFixtures.part("P2", lit),
                SolvePart.Choice.of("P3", List.of(science, new SolveOption.Sections("OPT-ENG",
                        List.of(eng)))));

        Random rng = new Random(261116L);
        Map<String, List<PathCandidate>> pools = new HashMap<>();
        CandidateSource source = group -> pools.computeIfAbsent(group.groupId(),
                ignored -> generatePool(rng, 2_000, group));
        PaperSolver solver = new PaperSolver();
        long t0 = System.nanoTime();
        SolveOutcome out = solver.solveAll(parts, source, SolveMode.check());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("PERF PaperSolver HSA11 ms=" + ms);

        assertThat(out.status()).isEqualTo(SolveStatus.VALID);
        assertThat(out.results()).hasSize(2);
        assertThat(out.stats().pathsEnumerated()).isEqualTo(11);
        assertThat(out.stats().subproblemsSolved()).isLessThanOrEqualTo(13);
        for (PathResult result : out.results()) {
            assertThat(result.status()).isEqualTo(SolveStatus.VALID);
            for (SubjectWitness witness : result.witness()) {
                int units = 0;
                for (GroupWitness group : witness.groups()) {
                    Map<String, PathCandidate> byId = new HashMap<>();
                    for (PathCandidate candidate : pools.get(group.groupId())) {
                        byId.put(candidate.questionId(), candidate);
                    }
                    for (QuestionPick pick : group.picks()) {
                        assertThat(byId).containsKey(pick.questionId());
                        units += byId.get(pick.questionId()).units().size();
                    }
                }
                assertThat(units).as("witness %s", witness.subjectCode())
                        .isEqualTo(witness.resolvedCount());
            }
        }
        // 2B 6.3/H3: kiểm tra giải mỗi môn ở số câu lớn nhất một lần → mỗi bộ ba một cách phân bổ,
        // C(5,3) = 10 SubjectPick; 12 là số phân bổ giấy B10 (chỉ sinh đề, không phải kiểm tra)
        assertThat(out.results().get(0).path().picks().get(0)).hasSize(10);
        PathResult scienceResult = out.results().get(0);
        for (SubjectPick pick : scienceResult.path().picks().get(0)) {
            // kiểm tra giải ở số câu lớn nhất của countsByPick (17/môn, 2B 6.3/H3): mỗi pick
            // đóng góp 51 câu thay vì 50 của phân bổ giấy → đề kiểm tra là 100 + 51 = 151
            int solvedUnits = 100;
            for (int i = 0; i < pick.sections().size(); i++) {
                String subject = pick.sections().get(i).subjectCode();
                int count = pick.counts().get(i);
                assertThat(scienceResult.witness().stream().anyMatch(
                        w -> w.subjectCode().equals(subject) && w.resolvedCount() == count)).isTrue();
                solvedUnits += count;
            }
            assertThat(solvedUnits).isEqualTo(151);
        }
        assertThat(out.results().get(1).witness().stream()
                .mapToInt(SubjectWitness::resolvedCount).sum()).isEqualTo(150);
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
                    units.add(SolverTestFixtures.unit(group.groupId() + "-" + i + "-u" + k,
                            k % 5 < 3 ? SolverTestFixtures.SC : SolverTestFixtures.SA, grade, diff));
                }
                out.add(new PathCandidate(group.groupId() + "-q" + i, 1,
                        group.groupId() + "-s" + i, null, units));
            } else {
                out.add(new PathCandidate(group.groupId() + "-q" + i, 1, null, null,
                        List.of(new CountingUnit(null, type, grade, diff, List.of(), List.of(),
                                BigDecimal.ONE))));
            }
        }
        return out;
    }
}
