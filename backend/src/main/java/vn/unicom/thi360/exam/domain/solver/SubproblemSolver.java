package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Giải một bài toán con theo môn (spec 5.4): chuẩn bị ({@link Prep}), kiểm nguồn thiếu rõ ràng,
 * rồi mạng luồng hai lớp ({@link MaxFlow}) khi áp dụng được hoặc quay lui trên lớp đóng góp
 * ({@link PathSearch}) cho phần còn lại.
 */
final class SubproblemSolver {

    private final Runnable deadlineCheck;

    SubproblemSolver(Runnable deadlineCheck) {
        this.deadlineCheck = deadlineCheck;
    }

    /** Kết quả của một bài toán con. */
    record Outcome(SolveStatus status, SolveReason reason, String groupId,
            List<GroupWitness> witness, QuotaRef failingQuota) {

        static Outcome valid(List<GroupWitness> witness) {
            return new Outcome(SolveStatus.VALID, null, null, witness, null);
        }

        static Outcome invalid(SolveReason reason, String groupId) {
            return new Outcome(SolveStatus.INVALID, reason, groupId, List.of(), null);
        }

        static Outcome invalid(SolveReason reason, String groupId, QuotaRef quota) {
            return new Outcome(SolveStatus.INVALID, reason, groupId, List.of(), quota);
        }

        static Outcome unconfirmed(SolveReason reason, String groupId) {
            return new Outcome(SolveStatus.UNCONFIRMED, reason, groupId, List.of(), null);
        }
    }

    /**
     * Giải một bài toán con.
     *
     * @param problem           các môn đã gộp (cùng subjectCode, count đã quyết)
     * @param candidatesByGroup song song các nhóm của problem (mọi {@link SectionOnPath} theo thứ tự)
     * @param apportioner       quy chỉ tiêu tỉ lệ
     */
    Outcome solve(Prep.Subproblem problem, List<List<PathCandidate>> candidatesByGroup,
            QuotaApportioner apportioner) {
        deadlineCheck.run();
        String[] failReason = new String[1];
        String[] failGroup = new String[1];
        Prep.SubproblemContext ctx = Prep.prepare(problem, candidatesByGroup, apportioner,
                deadlineCheck, failReason, failGroup);
        if (ctx == null) {
            // chỉ tiêu không khớp số câu / nhóm đơn vị cần không có nguồn usable nào
            return Outcome.invalid(SolveReason.INSUFFICIENT_SOURCE, failGroup[0]);
        }
        Shortage shortage = insufficiencyReason(ctx);
        if (shortage != null) {
            return Outcome.invalid(SolveReason.INSUFFICIENT_SOURCE, shortage.groupId(),
                    shortage.quota());
        }
        List<List<Integer>> picked;
        MaxFlow flow = new MaxFlow(deadlineCheck);
        if (flow.applies(ctx)) {
            picked = flow.solve(ctx);
            if (picked == null && flow.inapplicable()) {
                picked = new PathSearch(ctx, deadlineCheck).solve();
            }
        } else {
            picked = new PathSearch(ctx, deadlineCheck).solve();
        }
        if (picked == null) {
            // phân biệt lý do: không nhóm nào có phân hoạch/chỉ tiêu riêng → thất bại do không
            // xếp được chỉ tiêu cấp môn trên hợp nguồn (B9); còn lại: từng nhóm đủ nguồn nhưng
            // không xếp đồng thời được trên cả đường
            boolean subjectOnly = true;
            for (int g = 0; g < ctx.groups().size(); g++) {
                if (hasOwnDims(ctx, g)) {
                    subjectOnly = false;
                    break;
                }
            }
            return Outcome.invalid(
                    subjectOnly ? SolveReason.SUBJECT_QUOTA_INFEASIBLE
                            : SolveReason.NO_JOINT_SOLUTION,
                    null);
        }
        // đề chứng minh theo nhóm, theo thứ tự nhóm của problem
        List<GroupWitness> witness = new ArrayList<>(ctx.groups().size());
        for (int g = 0; g < ctx.groups().size(); g++) {
            List<QuestionPick> picks = new ArrayList<>();
            for (int i : picked.get(g)) {
                PathCandidate c = ctx.cands().get(g).get(i);
                picks.add(new QuestionPick(c.questionId(), c.versionNo()));
            }
            witness.add(new GroupWitness(ctx.groups().get(g).group().groupId(), picks));
        }
        return Outcome.valid(witness);
    }

    /**
     * Nguồn thiếu rõ ràng trước khi giải (spec 5.4): tổng nguồn usable của một nhóm nhỏ hơn số
     * đơn vị của nó, hoặc nguồn usable của một chỉ tiêu nhỏ hơn quota. Trả id nhóm thiếu (cho
     * {@link PathResult#groupId}), null khi đủ.
     */
    private record Shortage(String groupId, QuotaRef quota) {}

    private Shortage insufficiencyReason(Prep.SubproblemContext ctx) {
        int nG = ctx.groups().size();
        List<Prep.Requirement> reqs = ctx.reqs();
        int rCount = reqs.size();
        long[] supply = new long[rCount];
        java.util.Set<String> countedForSubject = new java.util.HashSet<>();
        int scans = 0;
        for (int g = 0; g < nG; g++) {
            deadlineCheck.run();
            int groupUnits = ctx.groups().get(g).units();
            long units = 0;
            for (int i = 0; i < ctx.cands().get(g).size(); i++) {
                if ((scans++ & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                if (ctx.usable()[g][i]) {
                    units += ctx.units()[g][i];
                    int[] vec = ctx.contrib()[g][i];
                    // chỉ tiêu của nhóm (kể cả TỔNG): chỉ tính trên ứng viên usable của chính nhóm
                    for (int r = ctx.reqFrom()[g]; r < ctx.reqTo()[g]; r++) {
                        if ((scans++ & 0x3FF) == 0) {
                            deadlineCheck.run();
                        }
                        supply[r] += vec[r];
                    }
                    // chỉ tiêu phía môn của section chứa nhóm g: hợp nguồn của các nhóm cùng
                    // section, mỗi ứng viên (theo khóa) đếm một lần (R2-3)
                    if (countedForSubject.add(ctx.sectionOf()[g] + ":"
                            + java.util.Arrays.toString(ctx.keys()[g][i]))) {
                        int[] blk = ctx.subjectBlocks()[ctx.sectionOf()[g]];
                        for (int r = blk[0]; r < blk[1]; r++) {
                            if ((scans++ & 0x3FF) == 0) {
                                deadlineCheck.run();
                            }
                            supply[r] += vec[r];
                        }
                    }
                }
            }
            if (units < groupUnits) {
                return new Shortage(ctx.groups().get(g).group().groupId(), null);
            }
        }
        for (int r = 0; r < rCount; r++) {
            if (supply[r] < reqs.get(r).quota()) {
                // tìm nhóm sở hữu chỉ tiêu để báo (chỉ tiêu phía môn không có nhóm riêng)
                String owner = null;
                for (int g = 0; g < nG; g++) {
                    if (r >= ctx.reqFrom()[g] && r < ctx.reqTo()[g]) {
                        owner = ctx.groups().get(g).group().groupId();
                        break;
                    }
                }
                return new Shortage(owner, ctx.reqRefs().get(r));
            }
        }
        return null;
    }

    /** Nhóm g có ít nhất một chỉ tiêu riêng (ô, năng lực, nhóm MAPID) ngoài TỔNG/phân hoạch môn. */
    private boolean hasOwnDims(Prep.SubproblemContext ctx, int g) {
        GroupQuota q = ctx.groups().get(g).group();
        return q.matrix() != null || !q.competencies().isEmpty()
                || !q.mapidGroups().isEmpty();
    }
}
