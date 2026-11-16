package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Các ca của ma trận theo môn (B9, spec mục 10) cho {@link PaperSolver}: chỉ tiêu dạng trả lời /
 * lớp × độ khó cấp môn đếm trên mọi đơn vị của mọi nhóm kể cả câu con của chùm, phạm vi MAPID
 * kế thừa (D23), {@code SUBJECT_CHOICE} theo D25, chỉ tiêu theo tỉ lệ quy bằng phần dư lớn nhất.
 */
class SubjectMatrixCasesTest {

    private final PaperSolver solver = new PaperSolver();

    @Test
    @DisplayName("Toán HSA 50 câu: 35 SINGLE_CHOICE + 15 SHORT_ANSWER, chùm lẫn hai dạng trừ đúng vào chỉ tiêu")
    void mathHsaAnswerQuotasWithMixedSets() {
        SubjectQuota quota = new SubjectQuota(
                new SubjectQuota.AnswerQuotas(List.of(
                        new DimensionQuota("SINGLE_CHOICE", 35),
                        new DimensionQuota("SHORT_ANSWER", 15)), QuotaUnit.COUNT),
                null);
        // 2 chùm × 3 câu con (2 SC + 1 SA mỗi chùm) + nhóm phần còn lại 44 câu
        GroupQuota setGroup = GroupQuota.set("gs", 2, 3, null);
        GroupQuota rest = GroupQuota.remainder("g-rest");
        SectionOnPath section = new SectionOnPath("MATH", "sec-math", 50, quota,
                List.of(setGroup, rest));

        List<PathCandidate> sets = new ArrayList<>();
        for (int s = 0; s < 3; s++) {
            sets.add(mixedSet("set" + s, 2, 1));
        }
        List<PathCandidate> singles = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            singles.add(typed("sc" + i, "SINGLE_CHOICE"));
        }
        for (int i = 0; i < 14; i++) {
            singles.add(typed("sa" + i, "SHORT_ANSWER"));
        }
        // thêm ứng viên không đúng dạng: không được chọn vì chỉ tiêu đầy
        singles.add(typed("tf0", "TRUE_FALSE"));

        CandidateSource source =
                g -> g == setGroup ? sets : singles;
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(section)), source,
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.witness()).hasSize(1);
        SubjectWitness sw = result.witness().get(0);
        assertThat(sw.subjectCode()).isEqualTo("MATH");
        assertThat(sw.resolvedCount()).isEqualTo(50);
        // nhóm chùm lấy đúng 2 bộ, nhóm còn lại 44 đơn vị
        for (GroupWitness gw : sw.groups()) {
            if (gw.groupId().equals("gs")) {
                assertThat(gw.picks()).hasSize(2);
            } else {
                assertThat(gw.picks()).hasSize(31 + 13);
            }
        }
    }

    @Test
    @DisplayName("chỉ tiêu lớp cấp môn trải trên hai nhóm: từng nhóm đủ nguồn nhưng tổng không đạt → INVALID nêu môn")
    void subjectQuotaAcrossGroupsInfeasible() {
        // môn cần G10=2, G11=4; g1 chỉ có câu G11, g2 chỉ có 1 câu G10 + thêm G11
        SubjectQuota quota = new SubjectQuota(null, new MatrixQuota.Separate(
                List.of(new DimensionQuota("G10", 2), new DimensionQuota("G11", 4)),
                List.of(), QuotaUnit.COUNT));
        GroupQuota g1 = GroupQuota.single("g1", 3);
        GroupQuota g2 = GroupQuota.single("g2", 3);
        SectionOnPath section = new SectionOnPath("ENG", "sec-eng", 6, quota, List.of(g1, g2));
        List<PathCandidate> src1 = List.of(graded("a1", "G11"), graded("a2", "G11"),
                graded("a3", "G11"));
        List<PathCandidate> src2 = List.of(graded("b1", "G10"), graded("b2", "G11"),
                graded("b3", "G11"));
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(section)),
                g -> g == g1 ? src1 : src2, SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.subjectCode()).isEqualTo("ENG");
        // R2-23: kết quả nêu đúng chỉ tiêu bị lỗi — chiều GRADE G10 cần 2 nhưng hợp nguồn chỉ có 1
        assertThat(result.failingQuota()).isNotNull();
        assertThat(result.failingQuota().subjectCode()).isEqualTo("ENG");
        assertThat(result.failingQuota().sectionId()).isEqualTo("sec-eng");
        assertThat(result.failingQuota().kind()).isEqualTo("GRADE");
        assertThat(result.failingQuota().key()).isEqualTo("G10");
    }

    @Test
    @DisplayName("chùm làm vượt chỉ tiêu dạng trả lời của môn thì không được chọn")
    void setExceedingSubjectQuotaRejected() {
        // môn 3 câu: SC=3, SA=0. Nhóm chùm 1×2 + nhóm phần còn lại 1.
        SubjectQuota quota = new SubjectQuota(
                new SubjectQuota.AnswerQuotas(List.of(
                        new DimensionQuota("SINGLE_CHOICE", 3),
                        new DimensionQuota("SHORT_ANSWER", 0)), QuotaUnit.COUNT),
                null);
        GroupQuota setGroup = GroupQuota.set("gs", 1, 2, null);
        GroupQuota rest = GroupQuota.remainder("g-rest");
        SectionOnPath section = new SectionOnPath("MATH", "sec", 3, quota,
                List.of(setGroup, rest));
        PathCandidate bad = mixedSet("bad", 1, 1); // {SC, SA}: lấy thì vượt SA=0
        PathCandidate good = mixedSet("good", 2, 0); // {SC, SC}
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(section)),
                g -> g == setGroup ? List.of(bad, good) : List.of(typed("x1", "SINGLE_CHOICE")),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        for (GroupWitness gw : result.witness().get(0).groups()) {
            if (gw.groupId().equals("gs")) {
                assertThat(gw.picks()).containsExactly(new QuestionPick("good", 1));
            }
        }

        // chỉ có chùm lệch → INVALID
        PathResult infeasible = solver.solvePath(SolvePath.of(0, List.of(section)),
                g -> g == setGroup ? List.of(bad) : List.of(typed("x1", "SINGLE_CHOICE")),
                SolveMode.check());
        assertThat(infeasible.status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    @DisplayName("MAPID: nhóm kế thừa phạm vi của môn; nút cha khớp câu gắn nút con (D23)")
    void mapidScopeInheritance() {
        // solver nhận nguồn đã lọc theo phạm vi có hiệu lực (D21/D23): nhóm g1 có phạm vi riêng
        // (nút cha K1 khớp câu gắn nút con K1.1), nhóm g2 kế thừa phạm vi môn (nút K2 khớp K2.1)
        GroupQuota g1 = GroupQuota.single("g1", 1);
        GroupQuota g2 = GroupQuota.single("g2", 1);
        SectionOnPath section = new SectionOnPath("BIO", "sec", 2, SubjectQuota.NONE,
                List.of(g1, g2));
        PathCandidate inOwnScope = unit("k1_1", "SINGLE_CHOICE");
        PathCandidate inInheritedScope = unit("k2_1", "SINGLE_CHOICE");
        List<GroupQuota> requested = new ArrayList<>();
        CandidateSource source = g -> {
            requested.add(g);
            return g == g1 ? List.of(inOwnScope) : List.of(inInheritedScope);
        };
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(section)), source,
                SolveMode.check());
        // solver phải hỏi nguồn cho đúng các nhóm của đường (không gộp, không bỏ sót)
        assertThat(requested).contains(g1, g2);
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
    }

    @Test
    @DisplayName("SUBJECT_CHOICE 5 chọn 3 gắn choiceGroup: nhóm đông nhận 17, thiểu số 16; ba NATURAL ra 3 phương án → 12 bài toán con đường, ≤10 bài toán con")
    void subjectChoiceGroupSplit() {
        // B10/D25: PHYS, CHEM, BIO = NATURAL; HIS, GEO = SOCIAL. countsByPick [17,17,16].
        // 10 bộ ba: 9 bộ 2-1 nhóm → 1 phân bổ mỗi bộ; bộ {PHYS,CHEM,BIO} → 3 phân bổ = 12 ca.
        // CHEM chỉ có 16 câu trong nguồn → mọi ca CHEM nhận 17 lỗi.
        String[] subjects = {"PHYS", "CHEM", "BIO", "HIS", "GEO"};
        String[] groups = {"NATURAL", "NATURAL", "NATURAL", "SOCIAL", "SOCIAL"};
        List<SectionOnPath> sections = new ArrayList<>();
        List<List<PathCandidate>> pools = new ArrayList<>();
        for (int s = 0; s < 5; s++) {
            GroupQuota g = GroupQuota.remainder("g-cs" + s);
            List<PathCandidate> pool = new ArrayList<>();
            int poolSize = s == 1 ? 16 : 20; // CHEM thiếu 1 câu cho nhịp 17
            for (int i = 0; i < poolSize; i++) {
                pool.add(typed("s" + s + "q" + i, "SINGLE_CHOICE"));
            }
            pools.add(pool);
            sections.add(new SectionOnPath(subjects[s], "sec-cs" + s, null,
                    SubjectQuota.NONE, List.of(g), groups[s]));
        }
        SubjectChoice choice = new SubjectChoice("opt-sc", sections, 3,
                List.of(17, 17, 16));
        CandidateSource source = g -> {
            for (int s = 0; s < sections.size(); s++) {
                if (sections.get(s).groups().get(0) == g) {
                    return pools.get(s);
                }
            }
            return List.of();
        };
        SolveOutcome outcome = solver.solveAll(
                List.of(SolvePart.Choice.of("p1", List.of(choice))), source,
                SolveMode.check());
        assertThat(outcome.status()).isEqualTo(SolveStatus.INVALID);
        // ca lỗi = mọi ca CHEM nhận 17:
        //   {PHYS,CHEM,HIS}=17-17-16, {PHYS,CHEM,GEO}=17-17-16, {CHEM,BIO,HIS}=17-17-16,
        //   {CHEM,BIO,GEO}=17-17-16, {CHEM,HIS,GEO}=17-16-17,
        //   và 2/3 phương án của bộ toàn NATURAL có CHEM ở nhịp 17.
        Set<String> expectedFailing = Set.of(
                "PHYS=17+CHEM=17+HIS=16", "PHYS=17+CHEM=17+GEO=16",
                "CHEM=17+BIO=17+HIS=16", "CHEM=17+BIO=17+GEO=16",
                "CHEM=17+HIS=17+GEO=16",
                "PHYS=17+CHEM=17+BIO=16", "PHYS=16+CHEM=17+BIO=17");
        Set<String> actualFailing = new HashSet<>();
        Set<String> allResults = new HashSet<>();
        for (PathResult r : outcome.results()) {
            for (List<SubjectPick> pickList : r.path().picks()) {
                for (SubjectPick p : pickList) {
                    allResults.add(pickKeyOf(p));
                }
            }
            for (SubjectPick p : r.failingSelections()) {
                actualFailing.add(pickKeyOf(p));
            }
        }
        assertThat(actualFailing).isEqualTo(expectedFailing);
        // đúng 12 ca cho 10 bộ ba (bộ toàn NATURAL bung thành 3 phương án)
        assertThat(allResults).hasSize(12);
        // tối đa 5 môn × 2 giá trị số câu = 10 bài toán con (B10)
        assertThat(outcome.stats().subproblemsSolved()).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("chỉ tiêu tỉ lệ quy đúng cho 16 và 17 câu; nhóm lấy phần còn lại nhận đúng số câu")
    void percentQuotasAndRemainderGroup() {
        // chỉ tiêu PERCENT 50/50: môn 17 câu → 9/8 (phần dư 0,5 hòa → mục khai trước); 16 → 8/8
        SubjectQuota quota = new SubjectQuota(
                new SubjectQuota.AnswerQuotas(List.of(
                        new DimensionQuota("SINGLE_CHOICE", 50),
                        new DimensionQuota("SHORT_ANSWER", 50)), QuotaUnit.PERCENT),
                null);
        GroupQuota setGroup = GroupQuota.set("gs", 1, 2, null);
        GroupQuota rest = GroupQuota.remainder("g-rest");

        SectionOnPath sec17 = new SectionOnPath("PHYS", "sec-17", 17, quota,
                List.of(setGroup, rest));
        List<PathCandidate> sets = List.of(mixedSet("setA", 2, 0));
        List<PathCandidate> pool17 = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            pool17.add(typed("a" + i, "SINGLE_CHOICE"));
        }
        for (int i = 0; i < 9; i++) {
            pool17.add(typed("b" + i, "SHORT_ANSWER"));
        }
        PathResult r17 = solver.solvePath(SolvePath.of(0, List.of(sec17)),
                g -> g == setGroup ? sets : pool17, SolveMode.check());
        assertThat(r17.status()).isEqualTo(SolveStatus.VALID);

        // môn 16 câu, nhóm chùm 2 đơn vị → nhóm phần còn lại phải nhận đúng 14 câu
        SectionOnPath sec16 = new SectionOnPath("CHEM", "sec-16", 16, quota,
                List.of(setGroup, rest));
        List<PathCandidate> pool16 = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            pool16.add(typed("c" + i, "SINGLE_CHOICE"));
        }
        for (int i = 0; i < 9; i++) {
            pool16.add(typed("d" + i, "SHORT_ANSWER"));
        }
        PathResult r16 = solver.solvePath(SolvePath.of(0, List.of(sec16)),
                g -> g == setGroup ? sets : pool16, SolveMode.check());
        assertThat(r16.status()).isEqualTo(SolveStatus.VALID);
        for (GroupWitness gw : r16.witness().get(0).groups()) {
            if (gw.groupId().equals("g-rest")) {
                assertThat(gw.picks()).hasSize(14);
            }
        }
    }

    // ------------------------------------------------------------ bộ phận dựng

    private static PathCandidate typed(String id, String answerType) {
        return PathCandidate.standalone(id, 1, answerType, "G10", "NB", BigDecimal.ONE);
    }

    private static PathCandidate graded(String id, String grade) {
        return PathCandidate.standalone(id, 1, "SINGLE_CHOICE", grade, "NB", BigDecimal.ONE);
    }

    private static PathCandidate unit(String id, String answerType) {
        return PathCandidate.standalone(id, 1, answerType, "G11", "TH", BigDecimal.ONE);
    }

    /** Chùm gồm {@code sc} câu SINGLE_CHOICE và {@code sa} câu SHORT_ANSWER. */
    private static PathCandidate mixedSet(String setId, int sc, int sa) {
        List<CountingUnit> units = new ArrayList<>();
        for (int i = 0; i < sc; i++) {
            units.add(new CountingUnit(setId + "-sc" + i, "SINGLE_CHOICE", "G10", "NB",
                    List.of(), List.of(), BigDecimal.ONE));
        }
        for (int i = 0; i < sa; i++) {
            units.add(new CountingUnit(setId + "-sa" + i, "SHORT_ANSWER", "G10", "NB",
                    List.of(), List.of(), BigDecimal.ONE));
        }
        return new PathCandidate(setId, 1, setId, null, null, units);
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
}
