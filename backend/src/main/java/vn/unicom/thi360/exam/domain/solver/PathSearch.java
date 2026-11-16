package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quay lui có cắt nhánh cho một bài toán con theo môn (spec 5.4 bước 2), trên <b>lớp đóng góp</b>
 * chứ không trên từng câu.
 *
 * <p>Một lớp đóng góp = một vector đóng góp trên toàn bộ chỉ tiêu (phía môn + chỉ tiêu các nhóm);
 * thành viên của lớp là các ứng viên usable của từng nhóm có cùng vector đó. Số lớp bị chặn bởi số
 * vector khác nhau, không tăng theo số ứng viên.
 *
 * <p>Ba tầng theo spec, lồng vào một phép duyệt duy nhất để đảm bảo duyệt hết:
 * <ol>
 *   <li><b>Chùm trước</b>: cặp (nhóm, lớp) của ứng viên nhiều đơn vị được duyệt trước — mỗi chùm
 *       một vector, lấy nguyên.</li>
 *   <li><b>Số theo lớp</b>: với từng cặp (nhóm g, lớp c) quyết định số ứng viên lấy từ c. Cắt nhánh
 *       bằng cận cung còn lại của từng chỉ tiêu (suffix supply), số đơn vị còn thiếu của nhóm và số
 *       thành viên còn lại của lớp (thành viên dùng chung giữa các nhóm).</li>
 *   <li><b>Câu cụ thể</b>: ngay khi chốt số take của một cặp, duyệt tổ hợp thành viên của cặp đó
 *       không trùng khóa (questionId/childId) với thành viên đã chọn; thất bại thì quay lui ngay
 *       trong cặp trước khi thử số take khác — do đó kết quả là đầy đủ, không sót nghiệm vì xung
 *       đột khóa giữa các nhóm.</li>
 * </ol>
 *
 * <p>Thứ tự duyệt cố định (CHECK) hay theo danh sách đã xáo (GENERATE): lớp theo thứ tự xuất hiện
 * đầu tiên trong danh sách ứng viên, thành viên theo thứ tự ứng viên.
 */
final class PathSearch {

    private final Prep.SubproblemContext ctx;
    private final Runnable deadlineCheck;

    // lớp đóng góp
    private final List<int[]> classVecs = new ArrayList<>();
    private final Map<String, Integer> classOf = new HashMap<>();
    private final List<List<int[]>> members = new ArrayList<>(); // members[c] = các (g, i)
    private int[] consumed; // số thành viên đã lấy của từng lớp

    // cặp (nhóm, lớp) theo thứ tự duyệt
    private int nPairs;
    private int[] pairGroup;
    private int[] pairClass;
    private List<int[]>[] pairMembers; // thành viên (g, c) theo thứ tự ứng viên
    private int[] pairUnits;   // đơn vị một thành viên của cặp
    private int[][] pairVec;   // vector đóng góp của cặp
    private int[][] suffixMax; // suffixMax[p][r] cung tối đa còn lại của chỉ tiêu r từ cặp p trở đi
    private int[][] suffixUnits; // suffixUnits[p][g] đơn vị tối đa còn lấy của nhóm g từ cặp p

    private int[] rem;       // chỉ tiêu còn thiếu
    private int[] remUnits;  // đơn vị còn thiếu của từng nhóm
    private int[] take;      // take[p] số thành viên đã lấy của cặp p
    private List<int[]>[] chosen; // thành viên đã chọn của từng cặp
    private final Set<String> usedKeys = new HashSet<>();
    private long steps;

    PathSearch(Prep.SubproblemContext ctx, Runnable deadlineCheck) {
        this.ctx = ctx;
        this.deadlineCheck = deadlineCheck;
    }

    /**
     * Chạy tìm kiếm.
     *
     * @return đề chứng minh theo nhóm (chỉ số ứng viên mỗi nhóm), null khi duyệt hết mà không có
     *         lời giải
     */
    List<List<Integer>> solve() {
        buildClasses();
        buildPairs();
        int rCount = ctx.reqs().size();
        rem = new int[rCount];
        for (int r = 0; r < rCount; r++) {
            rem[r] = ctx.reqs().get(r).quota();
        }
        remUnits = new int[ctx.groups().size()];
        for (int g = 0; g < remUnits.length; g++) {
            remUnits[g] = ctx.groups().get(g).units();
        }
        consumed = new int[classVecs.size()];
        take = new int[nPairs];
        @SuppressWarnings("unchecked")
        List<int[]>[] ch = new List[nPairs];
        chosen = ch;
        if (!dfsPairs(0)) {
            return null;
        }
        List<List<Integer>> picked = new ArrayList<>(ctx.groups().size());
        for (int g = 0; g < ctx.groups().size(); g++) {
            picked.add(new ArrayList<>());
        }
        for (int p = 0; p < nPairs; p++) {
            if (chosen[p] != null) {
                for (int[] m : chosen[p]) {
                    picked.get(pairGroup[p]).add(m[1]);
                }
            }
        }
        return picked;
    }

    // ---------------------------------------------------------------- lớp đóng góp

    /** Gom ứng viên usable thành lớp theo vector đóng góp; thành viên = (nhóm, chỉ số ứng viên). */
    private void buildClasses() {
        int nG = ctx.groups().size();
        for (int g = 0; g < nG; g++) {
            List<PathCandidate> pool = ctx.cands().get(g);
            for (int i = 0; i < pool.size(); i++) {
                if ((i & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                if (!ctx.usable()[g][i]) {
                    continue;
                }
                int[] vec = ctx.contrib()[g][i];
                Integer c = classOf.get(Arrays.toString(vec));
                if (c == null) {
                    c = classVecs.size();
                    classOf.put(Arrays.toString(vec), c);
                    classVecs.add(vec);
                    members.add(new ArrayList<>());
                }
                members.get(c).add(new int[]{g, i});
            }
        }
    }

    /**
     * Cặp (nhóm, lớp) theo thứ tự duyệt: cặp của ứng viên nhiều đơn vị (chùm) trước, rồi theo
     * nhóm, rồi thứ tự lớp. Mỗi (g, c) xuất hiện đúng một lần vì thành viên được liệt kê theo
     * nhóm rồi chỉ số ứng viên.
     */
    @SuppressWarnings("unchecked")
    private void buildPairs() {
        List<int[]> pairs = new ArrayList<>();
        for (int c = 0; c < classVecs.size(); c++) {
            int lastG = -1;
            for (int[] m : members.get(c)) {
                if (m[0] != lastG) {
                    pairs.add(new int[]{m[0], c});
                    lastG = m[0];
                }
            }
        }
        pairs.sort((a, b) -> {
            int ua = unitsOfMember(a[0], a[1]);
            int ub = unitsOfMember(b[0], b[1]);
            if (ua != ub) {
                return Integer.compare(ub, ua);
            }
            if (a[0] != b[0]) {
                return Integer.compare(a[0], b[0]);
            }
            return Integer.compare(a[1], b[1]);
        });
        nPairs = pairs.size();
        pairGroup = new int[nPairs];
        pairClass = new int[nPairs];
        pairMembers = new List[nPairs];
        pairUnits = new int[nPairs];
        pairVec = new int[nPairs][];
        int nG = ctx.groups().size();
        for (int p = 0; p < nPairs; p++) {
            int g = pairs.get(p)[0];
            int c = pairs.get(p)[1];
            pairGroup[p] = g;
            pairClass[p] = c;
            // vector charge của cặp: chỉ chỉ tiêu phía môn của section chứa nhóm + chỉ tiêu của
            // chính nhóm — đóng góp vào TỔNG của nhóm khác (matcher khớp mọi đơn vị) và chỉ tiêu
            // phía môn của section khác không được trừ ở cặp này (R2-3)
            int[] full = classVecs.get(c);
            int[] masked = new int[full.length];
            int[] blk = ctx.subjectBlocks()[ctx.sectionOf()[g]];
            for (int r = 0; r < full.length; r++) {
                if ((r >= blk[0] && r < blk[1]) || (r >= ctx.reqFrom()[g] && r < ctx.reqTo()[g])) {
                    masked[r] = full[r];
                }
            }
            pairVec[p] = masked;
            List<int[]> ms = new ArrayList<>();
            for (int[] m : members.get(c)) {
                if (m[0] == g) {
                    ms.add(m);
                }
            }
            pairMembers[p] = ms;
            pairUnits[p] = unitsOfMember(g, c);
        }
        // cận cung còn lại: mỗi chỉ tiêu, tổng tối đa có thể cộng từ cặp p trở đi
        int rCount = ctx.reqs().size();
        suffixMax = new int[nPairs + 1][rCount];
        suffixUnits = new int[nPairs + 1][nG];
        for (int p = nPairs - 1; p >= 0; p--) {
            if ((p & 0x3FF) == 0) {
                deadlineCheck.run();
            }
            int[] vec = pairVec[p];
            for (int r = 0; r < rCount; r++) {
                int q = ctx.reqs().get(r).quota();
                suffixMax[p][r] = suffixMax[p + 1][r]
                        + (int) Math.min((long) vec[r] * pairMembers[p].size(), (long) q);
            }
            for (int g = 0; g < nG; g++) {
                suffixUnits[p][g] = suffixUnits[p + 1][g];
            }
            suffixUnits[p][pairGroup[p]] += pairUnits[p] * pairMembers[p].size();
        }
    }

    private int unitsOfMember(int g, int c) {
        for (int[] m : members.get(c)) {
            if (m[0] == g) {
                return ctx.units()[g][m[1]];
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- duyệt số theo lớp + câu cụ thể

    /**
     * Duyệt cặp p: chọn số take rồi ngay tổ hợp thành viên; mọi lựa chọn đều có thể quay lui trước
     * khi thử số take tiếp theo.
     */
    private boolean dfsPairs(int p) {
        if ((steps++ & 0x3FF) == 0) {
            deadlineCheck.run();
        }
        if (p == nPairs) {
            for (int v : rem) {
                if (v > 0) {
                    return false;
                }
            }
            for (int v : remUnits) {
                if (v != 0) {
                    return false;
                }
            }
            return true;
        }
        // cắt nhánh: chỉ tiêu còn thiếu vượt cung tối đa, đơn vị còn thiếu vượt cung của nhóm
        int[] bound = suffixMax[p];
        for (int r = 0; r < rem.length; r++) {
            if (rem[r] > 0 && bound[r] < rem[r]) {
                return false;
            }
        }
        int g = pairGroup[p];
        if (remUnits[g] > 0 && remUnits[g] > suffixUnits[p][g]) {
            return false;
        }
        int c = pairClass[p];
        int[] vec = pairVec[p];
        int perUnits = pairUnits[p];
        // số take tối đa: theo thành viên của cặp, theo thành viên còn lại của lớp, theo đơn vị
        // còn thiếu của nhóm, và theo từng chỉ tiêu còn thiếu
        int maxTake = Math.min(pairMembers[p].size(), remUnits[g] / perUnits);
        for (int r = 0; r < vec.length; r++) {
            if (vec[r] > 0) {
                maxTake = Math.min(maxTake, rem[r] / vec[r]);
            }
        }
        maxTake = Math.min(maxTake, members.get(c).size() - consumed[c]);
        for (int t = maxTake; t >= 0; t--) {
            if (t > 0) {
                for (int r = 0; r < vec.length; r++) {
                    rem[r] -= vec[r] * t;
                }
                remUnits[g] -= perUnits * t;
                consumed[c] += t;
                take[p] = t;
                chosen[p] = new ArrayList<>(t);
                if (dfsMembers(p, 0, t)) {
                    return true;
                }
                chosen[p] = null;
            } else {
                take[p] = 0;
                if (dfsPairs(p + 1)) {
                    return true;
                }
            }
            if (t > 0) {
                for (int r = 0; r < vec.length; r++) {
                    rem[r] += vec[r] * t;
                }
                remUnits[g] += perUnits * t;
                consumed[c] -= t;
            }
            take[p] = 0;
        }
        return false;
    }

    /**
     * Chọn {@code need} thành viên của cặp p từ vị trí {@code pos}, không trùng khóa đã dùng; đủ số
     * thì sang cặp tiếp theo. Thất bại ở cặp sau cũng quay lui để thử tổ hợp thành viên khác.
     */
    private boolean dfsMembers(int p, int pos, int need) {
        if ((steps++ & 0x3FF) == 0) {
            deadlineCheck.run();
        }
        if (need == 0) {
            return dfsPairs(p + 1);
        }
        List<int[]> ms = pairMembers[p];
        if (pos >= ms.size()) {
            return false;
        }
        int g = pairGroup[p];
        int i = ms.get(pos)[1];
        String[] ks = ctx.keys()[g][i];
        boolean free = true;
        for (String k : ks) {
            if (usedKeys.contains(k)) {
                free = false;
                break;
            }
        }
        if (free) {
            for (String k : ks) {
                usedKeys.add(k);
            }
            chosen[p].add(ms.get(pos));
            if (dfsMembers(p, pos + 1, need - 1)) {
                return true;
            }
            chosen[p].remove(chosen[p].size() - 1);
            for (String k : ks) {
                usedKeys.remove(k);
            }
        }
        return dfsMembers(p, pos + 1, need);
    }
}
