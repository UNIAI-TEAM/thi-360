package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
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
        @SuppressWarnings("unchecked")
        List<int[]>[] ch = new List[nPairs];
        chosen = ch;
        if (!search()) {
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
            if ((c & 0x3FF) == 0) {
                deadlineCheck.run();
            }
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
            if ((p & 0x3FF) == 0) {
                deadlineCheck.run();
            }
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
     * Chữ ký trạng thái cụ thể của một nhánh duyệt (R2-10): đầy đủ trạng thái ảnh hưởng tới tính
     * khả thi của cây con — cặp/vị trí duyệt, chỉ tiêu và đơn vị còn thiếu, số thành viên đã dùng
     * của từng lớp, và tập khóa đã chiếm. Một trạng thái đã chứng minh bất khả thi không bao giờ
     * được duyệt lại (không liệt kê lặp các thành viên tương đương trong một lớp).
     */
    private record StateSig(int pair, int pos, int need, List<Integer> rem,
            List<Integer> remUnits, List<Integer> consumed, List<String> usedKeys) {
    }

    /** Trạng thái đã duyệt hết mà không có lời giải. */
    private final Set<StateSig> failed = new HashSet<>();

    private StateSig sig(int pair, int pos, int need) {
        List<Integer> r = new ArrayList<>(rem.length);
        for (int v : rem) {
            r.add(v);
        }
        List<Integer> ru = new ArrayList<>(remUnits.length);
        for (int v : remUnits) {
            ru.add(v);
        }
        List<Integer> cs = new ArrayList<>(consumed.length);
        for (int v : consumed) {
            cs.add(v);
        }
        return new StateSig(pair, pos, need, r, ru, cs, List.copyOf(usedKeys));
    }

    /** Khung duyệt một cặp (nhóm, lớp): thử số take từ lớn xuống, nhớ số take đang áp để thu hồi. */
    private final class PairsFrame {
        final int p;
        final int g;
        final int c;
        final int[] vec;
        final int perUnits;
        int nextT;      // số take sẽ thử tiếp (giảm dần), -1 khi khung hết nhánh
        int appliedT;   // số take đã áp lên trạng thái mà chưa thu hồi

        PairsFrame(int p) {
            this.p = p;
            this.g = pairGroup[p];
            this.c = pairClass[p];
            this.vec = pairVec[p];
            this.perUnits = pairUnits[p];
            // số take tối đa: theo thành viên của cặp, thành viên còn lại của lớp, đơn vị còn
            // thiếu của nhóm và từng chỉ tiêu còn thiếu
            int maxTake = Math.min(pairMembers[p].size(), remUnits[g] / perUnits);
            for (int r = 0; r < vec.length; r++) {
                if (vec[r] > 0) {
                    maxTake = Math.min(maxTake, rem[r] / vec[r]);
                }
            }
            this.nextT = Math.min(maxTake, members.get(c).size() - consumed[c]);
        }

        void apply(int t) {
            for (int r = 0; r < vec.length; r++) {
                rem[r] -= vec[r] * t;
            }
            remUnits[g] -= perUnits * t;
            consumed[c] += t;
            appliedT = t;
        }

        void undo() {
            if (appliedT <= 0) {
                return;
            }
            for (int r = 0; r < vec.length; r++) {
                rem[r] += vec[r] * appliedT;
            }
            remUnits[g] += perUnits * appliedT;
            consumed[c] -= appliedT;
            appliedT = 0;
        }
    }

    /**
     * Khung duyệt tổ hợp thành viên của một cặp: chọn {@code need} thành viên từ vị trí
     * {@code pos} trở đi, không trùng khóa đã dùng. Thành viên bị bỏ qua (nhánh skip) không được
     * chọn lại trong cùng lần duyệt — đúng ngữ nghĩa liệt kê tổ hợp của bản đệ quy.
     */
    private final class EnumFrame {
        final int p;
        int pos;
        int need;
        int[] pending; // thành viên đã lấy đang chờ kết quả nhánh con để hoàn tác

        EnumFrame(int p, int pos, int need) {
            this.p = p;
            this.pos = pos;
            this.need = need;
        }
    }

    private void pushPairs(Deque<Object> stack, int p) {
        if (p == nPairs) {
            // đích: mọi chỉ tiêu còn thiếu đã về 0 và đơn vị của từng nhóm đủ đúng
            for (int v : rem) {
                if (v > 0) {
                    return;
                }
            }
            for (int v : remUnits) {
                if (v != 0) {
                    return;
                }
            }
            terminalOk = true;
            stack.clear();
            return;
        }
        for (int r = 0; r < rem.length; r++) {
            if (rem[r] > suffixMax[p][r]) {
                return;
            }
        }
        for (int g = 0; g < remUnits.length; g++) {
            if (remUnits[g] > suffixUnits[p][g]) {
                return;
            }
        }
        if (failed.contains(sig(p, -1, 0))) {
            return;
        }
        stack.push(new PairsFrame(p));
    }

    private void pushEnum(Deque<Object> stack, int p, int pos, int need) {
        if (failed.contains(sig(p, pos, need))) {
            return;
        }
        stack.push(new EnumFrame(p, pos, need));
    }

    /** Necessary feasibility check on contribution counts, ignoring identity conflicts. */
    private boolean classFeasible(int p) {
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
        for (int r = 0; r < rem.length; r++) {
            if (rem[r] > suffixMax[p][r]) {
                return false;
            }
        }
        int g = pairGroup[p];
        if (remUnits[g] > suffixUnits[p][g]) {
            return false;
        }
        int[] vec = pairVec[p];
        int max = Math.min(pairMembers[p].size(), remUnits[g] / pairUnits[p]);
        for (int r = 0; r < vec.length; r++) {
            if (vec[r] > 0) {
                max = Math.min(max, rem[r] / vec[r]);
            }
        }
        for (int t = max; t >= 0; t--) {
            for (int r = 0; r < vec.length; r++) {
                rem[r] -= vec[r] * t;
            }
            remUnits[g] -= pairUnits[p] * t;
            boolean feasible = classFeasible(p + 1);
            remUnits[g] += pairUnits[p] * t;
            for (int r = 0; r < vec.length; r++) {
                rem[r] += vec[r] * t;
            }
            if (feasible) {
                return true;
            }
        }
        return false;
    }

    /**
     * Máy duyệt không đệ quy (R2-10): khung trên stack heap thay cho lời gọi đệ quy, nên độ sâu
     * duyệt theo số thành viên của lớp không còn giới hạn bởi stack Java. Trạng thái đã thất bại
     * được nhớ theo chữ ký đầy đủ (gồm tập khóa đã chiếm) để không liệt kê lặp các tổ hợp tương
     * đương của một lớp.
     *
     * @return true khi tìm được phân công khớp mọi chỉ tiêu và đơn vị
     */
    private boolean search() {
        if (!classFeasible(0)) {
            return false;
        }
        Deque<Object> stack = new ArrayDeque<>();
        pushPairs(stack, 0);
        while (!stack.isEmpty()) {
            if ((steps++ & 0x3FF) == 0) {
                deadlineCheck.run();
            }
            Object top = stack.peek();
            if (top instanceof PairsFrame f) {
                if (f.appliedT > 0) {
                    // nhánh enum vừa thất bại: thu hồi rồi thử số take kế tiếp
                    f.undo();
                    chosen[f.p] = null;
                }
                if (f.nextT < 0) {
                    stack.pop();
                    failed.add(sig(f.p, -1, 0));
                    continue;
                }
                int t = f.nextT--;
                if (t == 0) {
                    f.nextT = -1;
                    pushPairs(stack, f.p + 1);
                } else {
                    f.apply(t);
                    chosen[f.p] = new ArrayList<>(t);
                    pushEnum(stack, f.p, 0, t);
                }
                continue;
            }
            EnumFrame f = (EnumFrame) top;
            if (f.pending != null) {
                // nhánh con của thành viên đã lấy thất bại: hoàn tác rồi đi tiếp đuôi skip
                int g = pairGroup[f.p];
                int i = f.pending[1];
                for (String k : ctx.keys()[g][i]) {
                    usedKeys.remove(k);
                }
                List<int[]> ch = chosen[f.p];
                ch.remove(ch.size() - 1);
                f.pending = null;
                f.pos++;
            }
            List<int[]> ms = pairMembers[f.p];
            if (f.need == 0 || f.pos >= ms.size()) {
                stack.pop();
                failed.add(sig(f.p, f.pos, f.need));
                continue;
            }
            int g = pairGroup[f.p];
            int i = ms.get(f.pos)[1];
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
                chosen[f.p].add(ms.get(f.pos));
                f.pending = ms.get(f.pos);
                if (f.need == 1) {
                    pushPairs(stack, f.p + 1);
                } else {
                    pushEnum(stack, f.p, f.pos + 1, f.need - 1);
                }
            } else {
                f.pos++;
            }
        }
        return terminalOk;
    }

    /** Cờ đích: mọi chỉ tiêu và đơn vị khớp đúng khi duyệt hết các cặp. */
    private boolean terminalOk;
}
