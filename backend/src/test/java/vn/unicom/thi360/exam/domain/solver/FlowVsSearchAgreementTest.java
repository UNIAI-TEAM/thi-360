package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mạng luồng hai lớp và quay lui phải khớp nhau về tính khả thi (spec 5.4, plan D): với mọi bài
 * toán con mà {@link MaxFlow#applies} nhận (mọi nhóm SINGLE, mỗi phía tối đa một phân hoạch, không
 * chỉ tiêu ngoài phân hoạch, mọi ứng viên usable một đơn vị), hai thuật toán phải cùng trả nghiệm
 * hoặc cùng bó tay — và mọi đề chứng minh phải thỏa đúng chỉ tiêu và không dùng lặp một câu trên
 * đường.
 */
class FlowVsSearchAgreementTest {

    private final QuotaApportioner apportioner = new LargestRemainderApportioner();

    /** Ngữ cảnh đã chuẩn bị; null khi Prep báo rõ ràng bất khả thi. */
    private Prep.SubproblemContext ctx(Prep.Subproblem problem,
            List<List<PathCandidate>> pools) {
        return Prep.prepare(problem, pools, apportioner, () -> {
        }, new String[1], new String[1]);
    }

    /** Một đề chứng minh hợp lệ: đúng số đơn vị từng nhóm, đủ mọi chỉ tiêu, khóa không lặp. */
    private void assertWitnessValid(Prep.SubproblemContext ctx, List<List<Integer>> picked) {
        assertThat(picked).hasSameSizeAs(ctx.groups());
        Set<String> usedKeys = new HashSet<>();
        for (int g = 0; g < ctx.groups().size(); g++) {
            long units = 0;
            for (int i : picked.get(g)) {
                assertThat(ctx.usable()[g][i]).as("picked candidate usable").isTrue();
                units += ctx.units()[g][i];
                for (String k : ctx.keys()[g][i]) {
                    assertThat(usedKeys.add(k)).as("key %s dùng một lần", k).isTrue();
                }
            }
            assertThat(units)
                    .as("đơn vị nhóm %s", ctx.groups().get(g).group().groupId())
                    .isEqualTo(ctx.groups().get(g).units());
        }
        long[] sums = new long[ctx.reqs().size()];
        for (int g = 0; g < ctx.groups().size(); g++) {
            for (int i : picked.get(g)) {
                int[] vec = ctx.contrib()[g][i];
                // chỉ tiêu phía môn của section chứa nhóm g tính trên đơn vị của các nhóm cùng
                // section; chỉ tiêu của nhóm chỉ tính đơn vị nhóm đó (TỔNG của nhóm khác khớp
                // mọi đơn vị nên không được cộng vào)
                int[] blk = ctx.subjectBlocks()[ctx.sectionOf()[g]];
                for (int r = blk[0]; r < blk[1]; r++) {
                    sums[r] += vec[r];
                }
                for (int r = ctx.reqFrom()[g]; r < ctx.reqTo()[g]; r++) {
                    sums[r] += vec[r];
                }
            }
        }
        for (int r = 0; r < sums.length; r++) {
            assertThat(sums[r])
                    .as("chỉ tiêu %s", ctx.reqs().get(r).key())
                    .isEqualTo(ctx.reqs().get(r).quota());
        }
    }

    /** Hai thuật toán phải khớp tính khả thi; nếu giải được thì đề chứng minh phải hợp lệ. */
    private void agree(Prep.Subproblem problem, List<List<PathCandidate>> pools) {
        Prep.SubproblemContext ctx = ctx(problem, pools);
        assertThat(ctx).as("prep không rõ ràng bất khả thi").isNotNull();
        Runnable check = () -> {
        };
        MaxFlow flow = new MaxFlow(check);
        assertThat(flow.applies(ctx)).isTrue();
        List<List<Integer>> byFlow = flow.solve(ctx);
        if (byFlow == null && flow.inapplicable()) {
            return; // mô hình luồng không áp dụng (entry chung khóa lệch mục tiêu) — bỏ ca này
        }
        List<List<Integer>> bySearch = new PathSearch(ctx, check).solve();
        assertThat(byFlow != null).as("luồng và quay lui khớp tính khả thi")
                .isEqualTo(bySearch != null);
        if (byFlow != null) {
            assertWitnessValid(ctx, byFlow);
            assertWitnessValid(ctx, bySearch);
        }
    }

    private Prep.Subproblem problem(SectionOnPath section) {
        int count = section.questionCount();
        return new Prep.Subproblem(section.subjectCode(), count, List.of(section));
    }

    @Test
    @DisplayName("chỉ tiêu dạng trả lời cấp môn, hai nhóm câu đơn chia tổng")
    void subjectAnswerQuotasTwoGroups() {
        SubjectQuota quota = new SubjectQuota(
                SolverTestFixtures.answerQuotas(
                        SolverTestFixtures.dim(SolverTestFixtures.SC, 3),
                        SolverTestFixtures.dim(SolverTestFixtures.SA, 3)),
                null);
        SectionOnPath section = SolverTestFixtures.section("MATH", 6, quota,
                SolverTestFixtures.group("G-A", 4), GroupQuota.remainder("G-B"));
        List<PathCandidate> poolA = List.of(
                SolverTestFixtures.cand("a1", SolverTestFixtures.SC, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("a2", SolverTestFixtures.SC, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("a3", SolverTestFixtures.SC, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("a4", SolverTestFixtures.SA, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("a5", SolverTestFixtures.SA, SolverTestFixtures.G10, SolverTestFixtures.NB));
        List<PathCandidate> poolB = List.of(
                SolverTestFixtures.cand("b1", SolverTestFixtures.SA, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("b2", SolverTestFixtures.SC, SolverTestFixtures.G10, SolverTestFixtures.NB));
        agree(problem(section), List.of(poolA, poolB));
    }

    @Test
    @DisplayName("bất khả thi cả hai: không đủ câu SHORT_ANSWER cho chỉ tiêu môn")
    void subjectQuotaInfeasibleAgrees() {
        SubjectQuota quota = new SubjectQuota(
                SolverTestFixtures.answerQuotas(
                        SolverTestFixtures.dim(SolverTestFixtures.SC, 1),
                        SolverTestFixtures.dim(SolverTestFixtures.SA, 5)),
                null);
        SectionOnPath section = SolverTestFixtures.section("MATH", 6, quota,
                SolverTestFixtures.group("G-A", 6));
        List<PathCandidate> pool = List.of(
                SolverTestFixtures.cand("a1", SolverTestFixtures.SC, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("a2", SolverTestFixtures.SC, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("a3", SolverTestFixtures.SA, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("a4", SolverTestFixtures.SA, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("a5", SolverTestFixtures.SA, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("a6", SolverTestFixtures.SA, SolverTestFixtures.G10, SolverTestFixtures.NB));
        // cần 5 SA + 1 SC = 6, có 4 SA + 2 SC → bất khả thi
        agree(problem(section), List.of(pool));
    }

    @Test
    @DisplayName("nhóm có phân hoạch riêng (ô lớp × độ khó) + chỉ tiêu dạng trả lời của môn")
    void groupPartitionAndSubjectQuota() {
        MatrixQuota matrix = new MatrixQuota.Combined(List.of(
                SolverTestFixtures.cell(SolverTestFixtures.G10, SolverTestFixtures.NB, 2),
                SolverTestFixtures.cell(SolverTestFixtures.G12, SolverTestFixtures.VD, 2)));
        SectionOnPath section = SolverTestFixtures.section("MATH", 6,
                new SubjectQuota(
                        SolverTestFixtures.answerQuotas(
                                SolverTestFixtures.dim(SolverTestFixtures.SC, 4),
                                SolverTestFixtures.dim(SolverTestFixtures.SA, 2)),
                        null),
                SolverTestFixtures.group("G-C", 4, matrix), GroupQuota.remainder("G-R"));
        List<PathCandidate> poolC = List.of(
                SolverTestFixtures.cand("c1", SolverTestFixtures.SC, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("c2", SolverTestFixtures.SC, SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("c3", SolverTestFixtures.SC, SolverTestFixtures.G12, SolverTestFixtures.VD),
                SolverTestFixtures.cand("c4", SolverTestFixtures.SA, SolverTestFixtures.G12, SolverTestFixtures.VD),
                SolverTestFixtures.cand("c5", SolverTestFixtures.SC, SolverTestFixtures.G11, SolverTestFixtures.TH));
        List<PathCandidate> poolR = List.of(
                SolverTestFixtures.cand("r1", SolverTestFixtures.SA, SolverTestFixtures.G11, SolverTestFixtures.TH),
                SolverTestFixtures.cand("r2", SolverTestFixtures.SC, SolverTestFixtures.G11, SolverTestFixtures.TH),
                SolverTestFixtures.cand("r3", SolverTestFixtures.SC, SolverTestFixtures.G11, SolverTestFixtures.TH));
        agree(problem(section), List.of(poolC, poolR));
    }

    @Test
    @DisplayName("ứng viên chung hai nhóm: câu đó chỉ dùng một lần trên đường")
    void sharedCandidateKeysAgree() {
        SubjectQuota quota = new SubjectQuota(
                SolverTestFixtures.answerQuotas(
                        SolverTestFixtures.dim(SolverTestFixtures.SC, 3),
                        SolverTestFixtures.dim(SolverTestFixtures.SA, 3)),
                null);
        SectionOnPath section = SolverTestFixtures.section("MATH", 6, quota,
                SolverTestFixtures.group("G-A", 3), SolverTestFixtures.group("G-B", 3));
        // cùng một câu usable ở cả hai nhóm — chỉ được lấy cho một nhóm
        PathCandidate shared1 = SolverTestFixtures.cand("s1", SolverTestFixtures.SC,
                SolverTestFixtures.G10, SolverTestFixtures.NB);
        PathCandidate shared2 = SolverTestFixtures.cand("s2", SolverTestFixtures.SA,
                SolverTestFixtures.G10, SolverTestFixtures.NB);
        List<PathCandidate> poolA = List.of(shared1, shared2,
                SolverTestFixtures.cand("a-only", SolverTestFixtures.SC,
                        SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("a-only2", SolverTestFixtures.SA,
                        SolverTestFixtures.G10, SolverTestFixtures.NB));
        List<PathCandidate> poolB = List.of(shared1, shared2,
                SolverTestFixtures.cand("b-only", SolverTestFixtures.SC,
                        SolverTestFixtures.G10, SolverTestFixtures.NB),
                SolverTestFixtures.cand("b-only2", SolverTestFixtures.SA,
                        SolverTestFixtures.G10, SolverTestFixtures.NB));
        agree(problem(section), List.of(poolA, poolB));
    }

    @Test
    @DisplayName("phát sinh ngẫu nhiên có seed: phân hoạch môn + nhóm phân hoạch riêng")
    void randomizedAgreement() {
        String[] types = {SolverTestFixtures.SC, SolverTestFixtures.SA};
        String[] grades = {SolverTestFixtures.G10, SolverTestFixtures.G11, SolverTestFixtures.G12};
        String[] diffs = {SolverTestFixtures.NB, SolverTestFixtures.TH, SolverTestFixtures.VD};
        Random rng = new Random(20261116L);
        int trials = 0;
        while (trials < 8) {
            // một chiều lớp cấp môn (một phân hoạch), nhóm 1 có ma trận ô kết hợp (một phân hoạch)
            int needG10 = 1 + rng.nextInt(3);
            int needG12 = 6 - needG10;
            SubjectQuota quota = new SubjectQuota(null, new MatrixQuota.Separate(
                    List.of(SolverTestFixtures.dim(SolverTestFixtures.G10, needG10),
                            SolverTestFixtures.dim(SolverTestFixtures.G12, needG12)),
                    List.of()));
            MatrixQuota matrix = new MatrixQuota.Combined(List.of(
                    SolverTestFixtures.cell(SolverTestFixtures.G10, SolverTestFixtures.NB, 1),
                    SolverTestFixtures.cell(SolverTestFixtures.G12, SolverTestFixtures.VD, 1)));
            SectionOnPath section = SolverTestFixtures.section("SCI", 6, quota,
                    SolverTestFixtures.group("G-M", 2, matrix), GroupQuota.remainder("G-R"));
            List<PathCandidate> poolM = new ArrayList<>();
            List<PathCandidate> poolR = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                poolM.add(SolverTestFixtures.cand("m" + i, types[rng.nextInt(2)],
                        grades[rng.nextInt(3)], diffs[rng.nextInt(3)]));
                poolR.add(SolverTestFixtures.cand("r" + i, types[rng.nextInt(2)],
                        grades[rng.nextInt(3)], diffs[rng.nextInt(3)]));
            }
            Prep.Subproblem p = problem(section);
            if (ctx(p, List.of(poolM, poolR)) != null) {
                agree(p, List.of(poolM, poolR));
                trials++;
            }
        }
    }
}
