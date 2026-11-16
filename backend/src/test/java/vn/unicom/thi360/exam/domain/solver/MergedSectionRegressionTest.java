package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hồi quy vòng 2 của reviewer về môn gộp từ nhiều section cùng mã: chỉ tiêu cấp môn theo phạm vi
 * section sở hữu (R2-3/R2-28), nhóm lấy phần còn lại quyết theo section của nó (R2-4/R2-20/R2-26),
 * và tổng đơn vị các nhóm mỗi section bằng đúng số câu của section (R2-5/R2-27).
 */
class MergedSectionRegressionTest {

    private final PaperSolver solver = new PaperSolver();

    private static PathCandidate cand(String id, String answerType) {
        return PathCandidate.standalone(id, 1, answerType, "G10", "NB", BigDecimal.ONE);
    }

    private static SubjectQuota answerQuota(String code, int count) {
        return new SubjectQuota(
                new SubjectQuota.AnswerQuotas(List.of(new DimensionQuota(code, count)),
                        QuotaUnit.COUNT),
                null);
    }

    private static long totalPicks(PathResult result) {
        return result.witness().stream()
                .flatMap(w -> w.groups().stream())
                .mapToLong(g -> g.picks().size())
                .sum();
    }

    @Test
    @DisplayName("R2-3: hai section cùng mã, mỗi section chỉ tiêu riêng → VALID khi từng section thỏa")
    void sectionScopedSubjectQuotas() {
        GroupQuota g1 = GroupQuota.single("g1", 1);
        GroupQuota g2 = GroupQuota.single("g2", 1);
        SectionOnPath s1 = new SectionOnPath("MATH", "sec-1", 1, answerQuota("SINGLE_CHOICE", 1),
                List.of(g1));
        SectionOnPath s2 = new SectionOnPath("MATH", "sec-2", 1, answerQuota("SINGLE_CHOICE", 1),
                List.of(g2));
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(s1, s2)),
                g -> g == g1 ? List.of(cand("q1", "SINGLE_CHOICE"))
                        : List.of(cand("q2", "SINGLE_CHOICE")),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(totalPicks(result)).isEqualTo(2);
    }

    @Test
    void eachSectionUsesItsOwnPercentQuota() {
        GroupQuota a = GroupQuota.single("a", 1);
        GroupQuota b = GroupQuota.single("b", 1);
        SubjectQuota sc = new SubjectQuota(new SubjectQuota.AnswerQuotas(
                List.of(new DimensionQuota("SC", 100)), QuotaUnit.PERCENT), null);
        SubjectQuota sa = new SubjectQuota(new SubjectQuota.AnswerQuotas(
                List.of(new DimensionQuota("SA", 100)), QuotaUnit.PERCENT), null);
        SectionOnPath first = new SectionOnPath("M", "first", 1, sc, List.of(a));
        SectionOnPath second = new SectionOnPath("M", "second", 1, sa, List.of(b));
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(first, second)),
                g -> List.of(cand(g.groupId(), g == a ? "SC" : "SA")), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(totalPicks(result)).isEqualTo(2);
    }

    @Test
    @DisplayName("R2-3: section sau vi phạm chỉ tiêu của chính nó → INVALID")
    void laterSectionQuotaViolation() {
        GroupQuota g1 = GroupQuota.single("g1", 1);
        GroupQuota g2 = GroupQuota.single("g2", 1);
        SectionOnPath s1 = new SectionOnPath("MATH", "sec-1", 1, answerQuota("SINGLE_CHOICE", 1),
                List.of(g1));
        SectionOnPath s2 = new SectionOnPath("MATH", "sec-2", 1, answerQuota("SHORT_ANSWER", 1),
                List.of(g2));
        // g2 chỉ có SINGLE_CHOICE: chỉ tiêu SHORT_ANSWER của sec-2 không thỏa — không được tính
        // vào chỉ tiêu của sec-1
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(s1, s2)),
                g -> g == g1 ? List.of(cand("q1", "SINGLE_CHOICE"))
                        : List.of(cand("q2", "SINGLE_CHOICE")),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    @DisplayName("R2-4: hai section cùng mã có nhóm lấy phần còn lại → mỗi nhóm đúng phần của section mình")
    void remainderResolvedPerSection() {
        GroupQuota ra = GroupQuota.remainder("ra");
        GroupQuota rb = GroupQuota.remainder("rb");
        SectionOnPath s1 = new SectionOnPath("MATH", "sec-1", 1, SubjectQuota.NONE, List.of(ra));
        SectionOnPath s2 = new SectionOnPath("MATH", "sec-2", 1, SubjectQuota.NONE, List.of(rb));
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(s1, s2)),
                g -> List.of(cand(g.groupId() + "1", "SINGLE_CHOICE"),
                        cand(g.groupId() + "2", "SINGLE_CHOICE")),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(totalPicks(result)).isEqualTo(2);
        for (GroupWitness gw : result.witness().get(0).groups()) {
            assertThat(gw.picks()).hasSize(1);
        }
    }

    @Test
    @DisplayName("R2-5: tổng nhóm cố định dưới số câu section, không nhóm phần còn lại → INVALID")
    void underfilledSectionRejected() {
        GroupQuota g = GroupQuota.single("g1", 1);
        SectionOnPath section = new SectionOnPath("MATH", "sec-1", 2, SubjectQuota.NONE,
                List.of(g));
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(section)),
                x -> List.of(cand("q1", "SINGLE_CHOICE"), cand("q2", "SINGLE_CHOICE")),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
    }

    @Test
    @DisplayName("R2-5: section có số câu dương mà không có nhóm → INVALID")
    void positiveCountNoGroupsRejected() {
        SectionOnPath section = new SectionOnPath("MATH", "sec-1", 2, SubjectQuota.NONE,
                List.of());
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(section)),
                x -> List.of(cand("q1", "SINGLE_CHOICE")), SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.INVALID);
        assertThat(result.witness()).isEmpty();
    }

    @Test
    @DisplayName("R2-5: witness của ca VALID chứa đúng số đơn vị của section")
    void validWitnessHasExactUnits() {
        GroupQuota g = GroupQuota.single("g1", 2);
        SectionOnPath section = new SectionOnPath("MATH", "sec-1", 2, SubjectQuota.NONE,
                List.of(g));
        PathResult result = solver.solvePath(SolvePath.of(0, List.of(section)),
                x -> List.of(cand("q1", "SINGLE_CHOICE"), cand("q2", "SINGLE_CHOICE"),
                        cand("q3", "SINGLE_CHOICE")),
                SolveMode.check());
        assertThat(result.status()).isEqualTo(SolveStatus.VALID);
        assertThat(result.witness().get(0).resolvedCount()).isEqualTo(2);
        assertThat(totalPicks(result)).isEqualTo(2);
    }
}
