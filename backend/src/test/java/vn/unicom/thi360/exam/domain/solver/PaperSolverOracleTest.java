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

        // sai chỉ tiêu dạng trả lời của môn (2 câu đủ nhưng một câu không đúng dạng)
        CandidateSource wide = group -> List.of(q1, q2, q3);
        PathResult wrongType = PathResult.valid(path,
                List.of(new SubjectWitness("MATH", 2,
                        List.of(new GroupWitness("g1", List.of(
                                new QuestionPick("q1", 1), new QuestionPick("q3", 1)))))));
        assertThat(codes(validator.validate(wrongType, wide)))
                .contains(ViolationCode.SUBJECT_ANSWER_QUOTA);
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
        // 3 cách chọn: (CS0,CS1) 3+2 OK; (CS0,CS2) CS2=2 thiếu; (CS1,CS2) CS2=3 thiếu
        Map<String, SolveStatus> byPick = new LinkedHashMap<>();
        for (Map.Entry<String, BruteForceOracle.Eval> e : evals.entrySet()) {
            if (e.getKey().contains("=")) {
                byPick.put(e.getKey(), e.getValue().status());
            }
        }
        assertThat(byPick.values()).containsExactlyInAnyOrder(SolveStatus.VALID,
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
        static final String GROUP_COUNT_MISMATCH = "GROUP_COUNT_MISMATCH";
        static final String MISSING_WITNESS = "MISSING_WITNESS";
        static final String SUBJECT_ANSWER_QUOTA = "SUBJECT_ANSWER_QUOTA";
    }
}
