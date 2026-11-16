package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.solver.oracle.BruteForceOracle;
import vn.unicom.thi360.exam.domain.solver.oracle.OracleCase;
import vn.unicom.thi360.exam.domain.solver.oracle.OracleCase.Category;
import vn.unicom.thi360.exam.domain.solver.oracle.RandomCaseGenerator;
import vn.unicom.thi360.exam.domain.solver.oracle.WitnessValidator;
import vn.unicom.thi360.exam.domain.solver.oracle.WitnessValidator.Violation;

/**
 * Test oracle độc lập của {@link PaperSolver} (plan D, contract BE-5a-r2 §6):
 * {@link RandomCaseGenerator} sinh ≥ 500 ca nhỏ; {@link BruteForceOracle} kết luận VALID/INVALID
 * bằng duyệt vét cạn từ spec; {@link WitnessValidator} kiểm lại từng đề chứng minh mà bộ giải trả.
 * Test đỏ cho tới khi W-ALG hoàn tất thuật toán là điều kiện đúng.
 */
class PaperSolverOracleTest {

    private static final int TOTAL_CASES = 500;
    private static final int SUBJECT_QUOTA_SETS = 200;
    private static final int SUBJECT_CHOICE = 100;

    private final BruteForceOracle oracle = new BruteForceOracle();
    private final WitnessValidator validator = new WitnessValidator();
    private final PaperSolver solver = new PaperSolver();

    @Test
    @DisplayName("oracle đối chiếu bộ giải trên ≥ 500 ca ngẫu nhiên (≥200 chỉ tiêu môn + chùm, ≥100 SUBJECT_CHOICE)")
    void solverMatchesOracleOnGeneratedCases() {
        List<OracleCase> cases =
                new RandomCaseGenerator(0xBE5A1L).generate(TOTAL_CASES, SUBJECT_QUOTA_SETS,
                        SUBJECT_CHOICE);
        assertThat(cases).hasSizeGreaterThanOrEqualTo(TOTAL_CASES);
        long quotaSets =
                cases.stream().filter(c -> c.category() == Category.SUBJECT_QUOTA_SETS).count();
        long choices = cases.stream().filter(c -> c.category() == Category.SUBJECT_CHOICE).count();
        assertThat(quotaSets).isGreaterThanOrEqualTo(SUBJECT_QUOTA_SETS);
        assertThat(choices).isGreaterThanOrEqualTo(SUBJECT_CHOICE);

        Map<Category, int[]> counts = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            counts.put(c, new int[3]); // [cases, oracle VALID, solver-agree]
        }
        List<String> mismatches = new ArrayList<>();
        List<String> witnessViolations = new ArrayList<>();

        for (OracleCase testCase : cases) {
            Map<String, BruteForceOracle.Eval> expected =
                    oracle.evaluateAll(testCase.parts(), testCase.source());
            SolveOutcome outcome = solver.solveAll(testCase.parts(), testCase.source(),
                    SolveMode.check());
            int[] bucket = counts.get(testCase.category());
            bucket[0]++;

            // tổng hợp của oracle: INVALID khi một đường INVALID, ngược lại VALID
            boolean oracleAllValid =
                    expected.values().stream().allMatch(BruteForceOracle.Eval::valid);
            SolveStatus expectedStatus =
                    oracleAllValid ? SolveStatus.VALID : SolveStatus.INVALID;
            if (outcome.status() == expectedStatus) {
                bucket[2]++;
            } else {
                mismatches.add("case " + testCase.id() + " [" + testCase.category()
                        + "]: outcome " + outcome.status() + " != oracle " + expectedStatus);
            }

            for (PathResult result : outcome.results()) {
                BruteForceOracle.Eval eval = expected.get(BruteForceOracle.pathKey(result.path()));
                if (eval == null) {
                    mismatches.add("case " + testCase.id() + ": no oracle eval for path "
                            + BruteForceOracle.pathKey(result.path()));
                    continue;
                }
                if (result.status() != eval.status()) {
                    mismatches.add("case " + testCase.id() + ": path "
                            + BruteForceOracle.pathKey(result.path()) + " solver="
                            + result.status() + " oracle=" + eval.status());
                    continue;
                }
                if (eval.valid()) {
                    bucket[1]++;
                    List<Violation> violations = validator.validate(result, testCase.source());
                    for (Violation v : violations) {
                        witnessViolations.add("case " + testCase.id() + " "
                                + BruteForceOracle.pathKey(result.path()) + ": " + v);
                    }
                } else if (!result.failingSelections().isEmpty()) {
                    // đường SUBJECT_CHOICE INVALID: selections phải đúng các cách chọn lỗi
                    Set<String> actual = new HashSet<>();
                    for (SubjectPick p : result.failingSelections()) {
                        actual.add(pickKeyOf(p));
                    }
                    if (!actual.equals(eval.failingPickKeys())) {
                        mismatches.add("case " + testCase.id() + ": failing selections "
                                + actual + " != oracle " + eval.failingPickKeys());
                    }
                }
            }
        }

        for (Map.Entry<Category, int[]> e : counts.entrySet()) {
            int[] b = e.getValue();
            System.out.printf("[oracle] %-18s cases=%d oracleVALID=%d outcomeAgree=%d%n",
                    e.getKey(), b[0], b[1], b[2]);
        }
        assertThat(mismatches).as("solver/oracle mismatches").isEmpty();
        assertThat(witnessViolations).as("witness violations").isEmpty();
    }

    @Test
    @DisplayName("validator tự bắt lỗi: câu ngoài nguồn, thiếu đếm nhóm, dùng lặp, sai chỉ tiêu")
    void validatorRejectsBrokenWitnesses() {
        // một môn 2 câu, chỉ tiêu 2 SINGLE_CHOICE
        GroupQuota g = GroupQuota.single("g1", 2);
        SubjectQuota quota = new SubjectQuota(
                new SubjectQuota.AnswerQuotas(List.of(new DimensionQuota("SINGLE_CHOICE", 2)),
                        QuotaUnit.COUNT),
                null);
        SectionOnPath section = new SectionOnPath("MATH", "sec1", 2, quota, List.of(g));
        SolvePath path = SolvePath.of(0, List.of(section));
        PathCandidate q1 = standalone("q1", "SINGLE_CHOICE");
        PathCandidate q2 = standalone("q2", "SINGLE_CHOICE");
        PathCandidate q3 = standalone("q3", "SHORT_ANSWER");
        CandidateSource source = group -> List.of(q1, q2);

        PathResult valid = PathResult.valid(path,
                List.of(new SubjectWitness("MATH", 2,
                        List.of(new GroupWitness("g1", List.of(
                                new QuestionPick("q1", 1), new QuestionPick("q2", 1)))))));
        assertThat(validator.validate(valid, source)).isEmpty();

        // câu không có trong nguồn
        PathResult outsider = PathResult.valid(path,
                List.of(new SubjectWitness("MATH", 2,
                        List.of(new GroupWitness("g1", List.of(
                                new QuestionPick("q1", 1), new QuestionPick("q3", 1)))))));
        assertThat(codes(validator.validate(outsider, source)))
                .contains(ViolationCode.NOT_IN_SOURCE);

        // câu dùng lặp hai lần trong một nhóm
        PathResult reused = PathResult.valid(path,
                List.of(new SubjectWitness("MATH", 2,
                        List.of(new GroupWitness("g1", List.of(
                                new QuestionPick("q1", 1), new QuestionPick("q1", 1)))))));
        assertThat(codes(validator.validate(reused, source)))
                .contains(ViolationCode.ID_REUSED);

        // thiếu đếm của nhóm (1 câu thay 2)
        PathResult short_ = PathResult.valid(path,
                List.of(new SubjectWitness("MATH", 2,
                        List.of(new GroupWitness("g1", List.of(new QuestionPick("q1", 1)))))));
        assertThat(codes(validator.validate(short_, source)))
                .contains(ViolationCode.GROUP_COUNT_MISMATCH);

        // bỏ sót nhóm (witness rỗng)
        PathResult empty = PathResult.valid(path, List.of());
        assertThat(codes(validator.validate(empty, source)))
                .contains(ViolationCode.MISSING_WITNESS);

        // mục số câu dương không nhóm: đề VALID giả khai báo không witness → MISSING_WITNESS
        // (R2-21); witness rỗng cho bài toán con đó → SECTION_TOTAL_MISMATCH
        SectionOnPath noGroup = new SectionOnPath("MATH", "sec-ng", 2, SubjectQuota.NONE,
                List.of());
        SolvePath pathNg = SolvePath.of(0, List.of(noGroup));
        assertThat(oracle.evaluate(pathNg, source).status()).isEqualTo(SolveStatus.INVALID);
        PathResult forgedNoGroup = PathResult.valid(pathNg, List.of());
        assertThat(codes(validator.validate(forgedNoGroup, source)))
                .contains(ViolationCode.MISSING_WITNESS);
        PathResult emptyWitnessNoGroup = PathResult.valid(pathNg,
                List.of(new SubjectWitness("MATH", 2, List.of())));
        assertThat(codes(validator.validate(emptyWitnessNoGroup, source)))
                .contains(ViolationCode.SECTION_TOTAL_MISMATCH);

        // sai chỉ tiêu dạng trả lời của môn (2 câu đủ nhưng một câu không đúng dạng)
        CandidateSource wide = group -> List.of(q1, q2, q3);
        PathResult wrongType = PathResult.valid(path,
                List.of(new SubjectWitness("MATH", 2,
                        List.of(new GroupWitness("g1", List.of(
                                new QuestionPick("q1", 1), new QuestionPick("q3", 1)))))));
        assertThat(codes(validator.validate(wrongType, wide)))
                .contains(ViolationCode.SUBJECT_ANSWER_QUOTA);

        // lặp định danh ngay trong một ứng viên (R2-18): ứng viên 3 đơn vị có hai câu con cùng id
        PathCandidate dup = new PathCandidate("dup", 1, "dup", null, null, List.of(
                new CountingUnit("c0", "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(),
                        java.math.BigDecimal.ONE),
                new CountingUnit("c1", "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(),
                        java.math.BigDecimal.ONE),
                new CountingUnit("c1", "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(),
                        java.math.BigDecimal.ONE)));
        PathCandidate alias = new PathCandidate("al", 1, "al", null, null, List.of(
                new CountingUnit("al", "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(),
                        java.math.BigDecimal.ONE),
                new CountingUnit("c2", "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(),
                        java.math.BigDecimal.ONE),
                new CountingUnit("c3", "SINGLE_CHOICE", "G10", "NB", List.of(), List.of(),
                        java.math.BigDecimal.ONE)));
        SectionOnPath sec3 = new SectionOnPath("MATH", "sec3", 3, SubjectQuota.NONE,
                List.of(GroupQuota.single("g3", 3)));
        SolvePath path3 = SolvePath.of(0, List.of(sec3));
        CandidateSource dupSource = group -> List.of(dup, alias,
                standalone("qx", "SINGLE_CHOICE"));
        PathResult dupPick = PathResult.valid(path3,
                List.of(new SubjectWitness("MATH", 3,
                        List.of(new GroupWitness("g3", List.of(new QuestionPick("dup", 1)))))));
        assertThat(codes(validator.validate(dupPick, dupSource)))
                .contains(ViolationCode.INTERNAL_ID_REUSED)
                .doesNotContain(ViolationCode.ID_REUSED);
        PathResult aliasPick = PathResult.valid(path3,
                List.of(new SubjectWitness("MATH", 3,
                        List.of(new GroupWitness("g3", List.of(new QuestionPick("al", 1)))))));
        assertThat(codes(validator.validate(aliasPick, dupSource)))
                .contains(ViolationCode.INTERNAL_ID_REUSED);

        // mục không có nhóm nào mà đòi 2 câu: mọi đề chứng minh thiếu đúng 2 đơn vị (R2-21)
        SectionOnPath full = new SectionOnPath("MATH", "sec-f", 3, SubjectQuota.NONE,
                List.of(GroupQuota.single("g4", 3)));
        SectionOnPath emptySec = new SectionOnPath("MATH", "sec-e", 2, SubjectQuota.NONE,
                List.of());
        SolvePath merged = SolvePath.of(0, List.of(full, emptySec));
        PathCandidate a1 = standalone("a1", "SINGLE_CHOICE");
        PathCandidate a2 = standalone("a2", "SINGLE_CHOICE");
        PathCandidate a3 = standalone("a3", "SINGLE_CHOICE");
        CandidateSource aSource = group -> List.of(a1, a2, a3);
        PathResult covered = PathResult.valid(merged,
                List.of(new SubjectWitness("MATH", 5,
                        List.of(new GroupWitness("g4", List.of(new QuestionPick("a1", 1),
                                new QuestionPick("a2", 1), new QuestionPick("a3", 1)))))));
        assertThat(codes(validator.validate(covered, aSource)))
                .contains(ViolationCode.SECTION_TOTAL_MISMATCH);

        // đề chứng minh thừa câu so với số câu mục cũng bị bắt
        PathResult excess = PathResult.valid(path3,
                List.of(new SubjectWitness("MATH", 3,
                        List.of(new GroupWitness("g3", List.of(new QuestionPick("a1", 1),
                                new QuestionPick("a2", 1), new QuestionPick("a3", 1),
                                new QuestionPick("qx", 1)))))));
        assertThat(codes(validator.validate(excess, dupSource)))
                .contains(ViolationCode.GROUP_COUNT_MISMATCH,
                        ViolationCode.SECTION_TOTAL_MISMATCH);
    }

    @Test
    @DisplayName("R3-3: chỉ tiêu Combined (ô lớp × độ khó) — witness hợp lệ qua validator, giả sai ô bị bắt")
    void validatorCombinedMatrixCells() {
        // phạm vi MÔN: môn 2 câu, ô (G10,NB)=1 + (G11,TH)=1
        SubjectQuota cells2 = new SubjectQuota(null, new MatrixQuota.Combined(List.of(
                new CellQuota("G10", "NB", 1), new CellQuota("G11", "TH", 1)),
                QuotaUnit.COUNT));
        GroupQuota g = GroupQuota.single("g1", 2);
        SectionOnPath secSubj = new SectionOnPath("MATH", "sec-c", 2, cells2, List.of(g));
        PathCandidate nb = PathCandidate.standalone("q-nb", 1, "SINGLE_CHOICE", "G10", "NB",
                java.math.BigDecimal.ONE);
        PathCandidate th = PathCandidate.standalone("q-th", 1, "SINGLE_CHOICE", "G11", "TH",
                java.math.BigDecimal.ONE);
        PathCandidate off = PathCandidate.standalone("q-off", 1, "SINGLE_CHOICE", "G10", "TH",
                java.math.BigDecimal.ONE);
        CandidateSource src = group -> List.of(nb, th, off);

        PathResult subjectResult = solver.solvePath(SolvePath.of(0, List.of(secSubj)), src,
                SolveMode.check());
        assertThat(subjectResult.status()).isEqualTo(SolveStatus.VALID);
        assertThat(validator.validate(subjectResult, src)).isEmpty();

        // phạm vi NHÓM: chỉ tiêu Combined của chính nhóm
        GroupQuota gm = new GroupQuota("gm", GroupQuota.GroupKind.SINGLE, 1, null, null, null,
                null, new MatrixQuota.Combined(List.of(new CellQuota("G10", "NB", 1)),
                        QuotaUnit.COUNT),
                List.of(), List.of(), null, null);
        SectionOnPath secGroup = new SectionOnPath("ENG", "sec-g", 1, SubjectQuota.NONE,
                List.of(gm));
        CandidateSource gsrc = group -> List.of(nb, th);
        PathResult groupResult = solver.solvePath(SolvePath.of(0, List.of(secGroup)), gsrc,
                SolveMode.check());
        assertThat(groupResult.status()).isEqualTo(SolveStatus.VALID);
        assertThat(validator.validate(groupResult, gsrc)).isEmpty();

        // giả: witness chứa câu thuộc ô không khai → SUBJECT_CELL_QUOTA + UNDECLARED
        PathResult forgedSubj = PathResult.valid(subjectResult.path(),
                List.of(new SubjectWitness("MATH", 2,
                        List.of(new GroupWitness("g1", List.of(new QuestionPick("q-nb", 1),
                                new QuestionPick("q-off", 1)))))));
        assertThat(codes(validator.validate(forgedSubj, src)))
                .contains("SUBJECT_CELL_QUOTA", "SUBJECT_CELL_UNDECLARED");

        // giả phạm vi nhóm: câu sai ô so với chỉ tiêu Combined của nhóm
        PathResult forgedGroup = PathResult.valid(groupResult.path(),
                List.of(new SubjectWitness("ENG", 1,
                        List.of(new GroupWitness("gm", List.of(new QuestionPick("q-th", 1)))))));
        assertThat(codes(validator.validate(forgedGroup, gsrc)))
                .contains("GROUP_CELL_QUOTA");
    }

    @Test
    @DisplayName("R2-9: witness khóa theo bài toán con đầy đủ (môn + số câu + tập nhóm), không theo môn/số câu")
    void validatorKeysWitnessBySubproblem() {
        // một SUBJECT_CHOICE sinh hai bài toán con MATH cùng số câu nhưng khác nhóm
        GroupQuota m1 = GroupQuota.single("g-m1", 1);
        GroupQuota m2 = GroupQuota.single("g-m2", 1);
        GroupQuota a = GroupQuota.single("g-a", 1);
        GroupQuota b = GroupQuota.single("g-b", 1);
        List<SectionOnPath> sections = List.of(
                new SectionOnPath("MATH", "m1", null, SubjectQuota.NONE, List.of(m1)),
                new SectionOnPath("MATH", "m2", null, SubjectQuota.NONE, List.of(m2)),
                new SectionOnPath("A", "a", null, SubjectQuota.NONE, List.of(a)),
                new SectionOnPath("B", "b", null, SubjectQuota.NONE, List.of(b)));
        SubjectChoice choice = new SubjectChoice("opt", sections, 2, List.of(1, 1));
        SubjectPick pickMA = new SubjectPick("opt",
                List.of(sections.get(0), sections.get(2)), List.of(1, 1));
        SubjectPick pickMB = new SubjectPick("opt",
                List.of(sections.get(1), sections.get(3)), List.of(1, 1));
        SolvePath path = new SolvePath(0, List.of(new ChosenOption("p1", "opt")), List.of(),
                List.of(choice), List.of(List.of(pickMA, pickMB)));
        PathCandidate cm1 = standalone("cm1", "SINGLE_CHOICE");
        PathCandidate cm2 = standalone("cm2", "SINGLE_CHOICE");
        PathCandidate ca = standalone("ca", "SINGLE_CHOICE");
        PathCandidate cb = standalone("cb", "SINGLE_CHOICE");
        CandidateSource source = g -> g == m1 ? List.of(cm1)
                : g == m2 ? List.of(cm2) : g == a ? List.of(ca) : List.of(cb);

        // witness đầy đủ bốn bài toán con → không vi phạm
        PathResult good = PathResult.valid(path, List.of(
                new SubjectWitness("MATH", 1,
                        List.of(new GroupWitness("g-m1", List.of(new QuestionPick("cm1", 1))))),
                new SubjectWitness("MATH", 1,
                        List.of(new GroupWitness("g-m2", List.of(new QuestionPick("cm2", 1))))),
                new SubjectWitness("A", 1,
                        List.of(new GroupWitness("g-a", List.of(new QuestionPick("ca", 1))))),
                new SubjectWitness("B", 1,
                        List.of(new GroupWitness("g-b", List.of(new QuestionPick("cb", 1)))))));
        assertThat(validator.validate(good, source)).isEmpty();

        // witness của bài toán con {g-m1} nhặt ứng viên của m2 (ngoài nguồn của g-m1)
        // và bài toán con {g-m2} không có witness → báo riêng từng lỗi
        PathResult misplaced = PathResult.valid(path, List.of(
                new SubjectWitness("MATH", 1,
                        List.of(new GroupWitness("g-m1", List.of(new QuestionPick("cm2", 1))))),
                new SubjectWitness("A", 1,
                        List.of(new GroupWitness("g-a", List.of(new QuestionPick("ca", 1))))),
                new SubjectWitness("B", 1,
                        List.of(new GroupWitness("g-b", List.of(new QuestionPick("cb", 1)))))));
        assertThat(codes(validator.validate(misplaced, source)))
                .contains(ViolationCode.NOT_IN_SOURCE, ViolationCode.MISSING_WITNESS);
    }

    @Test
    @DisplayName("oracle: hai mục cùng môn giữ chỉ tiêu và phần còn lại của riêng mình (R2-3/R2-4)")
    void oracleSeparateSectionQuotas() {
        // mục A: chỉ tiêu SINGLE_CHOICE=1, nhóm ra; mục B: chỉ tiêu SINGLE_CHOICE=1, nhóm rb —
        // mỗi nhóm một nguồn riêng; VALID khi mỗi mục tự thỏa
        SubjectQuota sc = new SubjectQuota(new SubjectQuota.AnswerQuotas(
                List.of(new DimensionQuota("SINGLE_CHOICE", 1)), QuotaUnit.COUNT), null);
        SectionOnPath secA = new SectionOnPath("MATH", "sec-a", 1, sc,
                List.of(GroupQuota.remainder("ra")));
        SectionOnPath secB = new SectionOnPath("MATH", "sec-b", 1, sc,
                List.of(GroupQuota.remainder("rb")));
        CandidateSource ok = g -> "ra".equals(g.groupId())
                ? List.of(standalone("ra1", "SINGLE_CHOICE"), standalone("ra2", "SINGLE_CHOICE"))
                : List.of(standalone("rb1", "SINGLE_CHOICE"), standalone("rb2", "SINGLE_CHOICE"));
        assertThat(oracle.solveSubpath(List.of(secA, secB), ok)).isTrue();

        // mục B chỉ có câu sai dạng so với chỉ tiêu riêng của nó → INVALID
        CandidateSource bad = g -> "ra".equals(g.groupId())
                ? List.of(standalone("ra1", "SINGLE_CHOICE"), standalone("ra2", "SINGLE_CHOICE"))
                : List.of(standalone("rb1", "SHORT_ANSWER"));
        assertThat(oracle.solveSubpath(List.of(secA, secB), bad)).isFalse();

        // chỉ tiêu của mục A không được trừ vào nhóm của mục B: mục A thiếu dạng khai,
        // mục B dư đúng dạng đó — vẫn INVALID dù tổng môn nhìn như đủ
        SubjectQuota scB = new SubjectQuota(new SubjectQuota.AnswerQuotas(
                List.of(new DimensionQuota("SHORT_ANSWER", 1)), QuotaUnit.COUNT), null);
        SectionOnPath secB2 = new SectionOnPath("MATH", "sec-b2", 1, scB,
                List.of(GroupQuota.remainder("rb")));
        CandidateSource swapped = g -> "ra".equals(g.groupId())
                ? List.of(standalone("ra1", "SHORT_ANSWER"))
                : List.of(standalone("rb1", "SHORT_ANSWER"));
        assertThat(oracle.solveSubpath(List.of(secA, secB2), swapped)).isFalse();

        // đường có mục khai ít hơn số câu mục (2 câu, một nhóm cố định 1) → INVALID cấu trúc
        SectionOnPath short_ = new SectionOnPath("MATH", "sec-short", 2, SubjectQuota.NONE,
                List.of(GroupQuota.single("g1", 1)));
        assertThat(oracle.solveSubpath(List.of(short_),
                g -> List.of(standalone("x1", "SINGLE_CHOICE"),
                        standalone("x2", "SINGLE_CHOICE")))).isFalse();
        SectionOnPath emptySec = new SectionOnPath("MATH", "sec-empty", 2, SubjectQuota.NONE,
                List.of());
        assertThat(oracle.solveSubpath(List.of(emptySec), g -> List.of())).isFalse();
    }

    @Test
    @DisplayName("oracle kiểm chứng tay: hai nhóm tranh q1 → INVALID; đủ hai câu → VALID")
    void oracleGroundTruth() {
        GroupQuota a = GroupQuota.single("a", 1);
        GroupQuota b = GroupQuota.single("b", 1);
        SectionOnPath section = new SectionOnPath("S", "sec", 2, SubjectQuota.NONE, List.of(a, b));
        SolvePath path = SolvePath.of(0, List.of(section));
        PathCandidate q1 = standalone("q1", "SINGLE_CHOICE");
        PathCandidate q2 = standalone("q2", "SINGLE_CHOICE");

        assertThat(oracle.solveSubpath(List.of(section),
                group -> group == a ? List.of(q1) : List.of(q1))).isFalse();
        assertThat(oracle.solveSubpath(List.of(section),
                group -> group == a ? List.of(q1) : List.of(q2))).isTrue();
        assertThat(oracle.evaluate(path,
                group -> group == a ? List.of(q1) : List.of(q1, q2)).status())
                .isEqualTo(SolveStatus.VALID);
    }

    @Test
    @DisplayName("oracle: cách chọn môn SUBJECT_CHOICE 3 chọn 2, một cách thiếu nguồn → INVALID đúng pick")
    void oraclePickLevelVerdicts() {
        List<SectionOnPath> sections = new ArrayList<>();
        Map<GroupQuota, List<PathCandidate>> sources = new LinkedHashMap<>();
        for (int s = 0; s < 3; s++) {
            GroupQuota g = GroupQuota.remainder("g" + s);
            List<PathCandidate> pool = new ArrayList<>();
            int count = s == 2 ? 1 : 4; // môn CS2 thiếu nguồn cho count 3
            for (int i = 0; i < count; i++) {
                pool.add(standalone("s" + s + "q" + i, "SINGLE_CHOICE"));
            }
            sources.put(g, pool);
            sections.add(new SectionOnPath("CS" + s, "sec" + s, null, SubjectQuota.NONE,
                    List.of(g)));
        }
        SubjectChoice choice = new SubjectChoice("opt", sections, 2, List.of(3, 2));
        SolvePart part = SolvePart.Choice.of("p1", List.of(choice));
        CandidateSource source = g -> sources.getOrDefault(g, List.of());

        Map<String, BruteForceOracle.Eval> evals = oracle.evaluateAll(List.of(part), source);
        // nhóm null tính một nhóm chung: mỗi bộ môn có 2 phân bổ đối xứng (môn i nhận lo=2).
        // (CS0,CS1): cả hai phân bổ đều đủ nguồn → 2 VALID; hai bộ chứa CS2 — CS2 chỉ có
        // 1 câu nên mọi phân bổ đều thiếu → 4 INVALID.
        Map<String, SolveStatus> byPick = new LinkedHashMap<>();
        for (Map.Entry<String, BruteForceOracle.Eval> e : evals.entrySet()) {
            if (e.getKey().contains("=")) {
                byPick.put(e.getKey(), e.getValue().status());
            }
        }
        assertThat(byPick.values()).containsExactlyInAnyOrder(SolveStatus.VALID,
                SolveStatus.VALID, SolveStatus.INVALID, SolveStatus.INVALID,
                SolveStatus.INVALID, SolveStatus.INVALID);
    }

    private static PathCandidate standalone(String id, String answerType) {
        return PathCandidate.standalone(id, 1, answerType, "G10", "NB",
                java.math.BigDecimal.ONE);
    }

    private static Set<String> codes(List<Violation> violations) {
        Set<String> out = new HashSet<>();
        for (Violation v : violations) {
            out.add(v.code());
        }
        return out;
    }

    private static String pickKeyOf(SubjectPick pick) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pick.sections().size(); i++) {
            if (i > 0) {
                sb.append('+');
            }
            sb.append(pick.sections().get(i).subjectCode()).append('=').append(pick.counts().get(i));
        }
        return sb.toString();
    }

    /** Hằng chuỗi mã lỗi của {@link Violation} (không ép kiểu enum để giữ validator tự do). */
    private static final class ViolationCode {
        static final String NOT_IN_SOURCE = "NOT_IN_SOURCE";
        static final String ID_REUSED = "ID_REUSED";
        static final String INTERNAL_ID_REUSED = "INTERNAL_ID_REUSED";
        static final String GROUP_COUNT_MISMATCH = "GROUP_COUNT_MISMATCH";
        static final String SECTION_TOTAL_MISMATCH = "SECTION_TOTAL_MISMATCH";
        static final String MISSING_WITNESS = "MISSING_WITNESS";
        static final String UNKNOWN_GROUP = "UNKNOWN_GROUP";
        static final String SUBJECT_ANSWER_QUOTA = "SUBJECT_ANSWER_QUOTA";
    }
}
