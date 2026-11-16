package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tìm kiếm quay lui trên cả một đường lựa chọn (spec 5.4, trường hợp tổng quát).
 *
 * <p>Quyết định cho mỗi ứng viên là nhị phân: chọn nguyên vẹn (bộ câu hỏi và Điền khuyết luôn lấy trọn)
 * hoặc bỏ qua. Một ứng viên chỉ được chọn khi toàn bộ vector đóng góp của nó không vượt chỉ tiêu còn lại
 * của khối và không khóa nào của nó (questionId, childIds) đã được dùng trên đường — vì vậy một câu con
 * vừa nằm trong bộ vừa có bản độc lập sẽ xung đột đúng như ràng buộc.
 *
 * <p>Khối được duyệt theo độ khó giảm dần (ít ứng viên so với nhu cầu trước); trong khối, ứng viên được
 * quét theo thứ tự đầu vào và nhắm vào chỉ tiêu còn thiếu "chặt" nhất. Cắt nhánh bằng cận cung còn lại
 * của từng chỉ tiêu (suffix supply): nếu tổng tối đa có thể cộng vào một chỉ tiêu nhỏ hơn lượng còn thiếu
 * thì nhánh này chết. Ngăn xếp tường minh, không đệ quy — an toàn với danh sách ứng viên lớn.
 *
 * <p>Thuật toán đầy đủ: cây quyết định duyệt hết mọi tập con của danh sách ứng viên theo thứ tự cố định;
 * vì thứ tự ứng viên không ảnh hưởng đến tính khả thi, không mất nghiệm.
 */
final class PathSearch {

    private final List<BlockContext> blocks;
    private final Runnable deadlineCheck;
    private final Set<String> used = new HashSet<>();
    private final Deque<BFrame> stack = new ArrayDeque<>();
    private List<List<Integer>> witness;
    private long steps;

    /**
     * @param searchOrder   các khối đã chuẩn bị, theo thứ tự giải (khó trước)
     * @param deadlineCheck ném {@link DeadlineHit} khi hết hạn
     */
    PathSearch(List<BlockContext> searchOrder, Runnable deadlineCheck) {
        this.blocks = searchOrder;
        this.deadlineCheck = deadlineCheck;
    }

    /**
     * Chạy tìm kiếm.
     *
     * @return true khi tìm được cách chọn thỏa mọi chỉ tiêu của mọi khối; khi đó {@link #witness()}
     *         chứa chỉ số ứng viên đã chọn theo thứ tự {@code searchOrder}
     */
    boolean solve() {
        BFrame current = null;
        while (true) {
            if (current == null) {
                if (stack.size() == blocks.size()) {
                    collect();
                    return true;
                }
                current = newFrame(stack.size());
            }
            if (advance(current)) {
                stack.push(current);
                current = null;
                continue;
            }
            // khung thất bại: quay lui trong chính khung — thử nhánh "bỏ qua" của lần chọn cuối
            while (current.takeCount > 0) {
                undoLastTake(current);
                if (advance(current)) {
                    break;
                }
            }
            if (current.takeCount > 0) {
                stack.push(current);
                current = null;
                continue;
            }
            current = null;
            while (true) {
                deadlineCheck.run();
                if (stack.isEmpty()) {
                    return false;
                }
                BFrame top = stack.pop();
                if (top.takeCount == 0) {
                    continue;
                }
                undoLastTake(top);
                current = top;
                break;
            }
        }
    }

    /** Chỉ số ứng viên đã chọn của từng khối (theo thứ tự {@code searchOrder}); gọi sau {@link #solve()} == true. */
    List<List<Integer>> witness() {
        return witness;
    }


    // ---- một khối ----

    private void collect() {
        List<List<Integer>> out = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            out.add(List.of());
        }
        for (BFrame f : stack) {
            out.set(f.b, f.selected);
        }
        witness = out;
    }

    private BFrame newFrame(int b) {
        BlockContext ctx = blocks.get(b);
        int n = ctx.cands().size();
        int rCount = ctx.reqs().size();
        int[] order = new int[n];
        int len = 0;
        boolean[] usable = ctx.usable();
        String[][] keys = ctx.keys();
        for (int i = 0; i < n; i++) {
            if ((i & 0x3FF) == 0) {
                deadlineCheck.run();
            }
            if (usable[i] && !conflicts(keys[i])) {
                order[len++] = i;
            }
        }
        BFrame f = new BFrame(b, order, len, rCount);
        for (int r = 0; r < rCount; r++) {
            f.rem[r] = ctx.reqs().get(r).quota();
        }
        // suffix supply: mỗi chỉ tiêu, tổng tối đa còn cộng được từ vị trí pos trở đi;
        // suffixUnits: tổng đơn vị tối đa còn lấy được (để so với questionCount)
        int[][] suffixMax = new int[len + 1][rCount];
        int[] suffixUnits = new int[len + 1];
        int[][] contrib = ctx.contrib();
        int[] units = ctx.unitsProvided();
        for (int pos = len - 1; pos >= 0; pos--) {
            if ((pos & 0x3FF) == 0) {
                deadlineCheck.run();
            }
            int[] vec = contrib[order[pos]];
            int[] row = suffixMax[pos];
            int[] next = suffixMax[pos + 1];
            for (int r = 0; r < rCount; r++) {
                int q = ctx.reqs().get(r).quota();
                row[r] = next[r] + Math.min(vec[r], q);
            }
            suffixUnits[pos] = suffixUnits[pos + 1] + units[order[pos]];
        }
        f.suffixMax = suffixMax;
        f.suffixUnits = suffixUnits;
        return f;
    }

    /**
     * Đẩy khung tìm kiếm của một khối tới khi khối đủ chỉ tiêu hoặc hết khả năng.
     *
     * @return true khi khối đã đủ mọi chỉ tiêu và đủ đúng {@code questionCount} đơn vị; false khi đã quét
     *         hết mà chưa đủ (các lần chọn đã ghi vẫn còn trong khung để quay lui)
     */
    private boolean advance(BFrame f) {
        BlockContext ctx = blocks.get(f.b);
        int rCount = ctx.reqs().size();
        int[][] contrib = ctx.contrib();
        int[] units = ctx.unitsProvided();
        int qc = ctx.quota().questionCount();
        int[] rem = f.rem;
        for (;;) {
            if ((steps++ & 0x3FF) == 0) {
                deadlineCheck.run();
            }
            // cắt nhánh: chỉ tiêu còn thiếu vượt cung tối đa, hoặc đơn vị không đủ/vượt questionCount
            int[] bound = f.suffixMax[f.pos];
            boolean allMet = true;
            for (int r = 0; r < rCount; r++) {
                if (rem[r] <= 0) {
                    continue;
                }
                allMet = false;
                if (bound[r] < rem[r]) {
                    return false;
                }
            }
            if (f.unitsTaken + f.suffixUnits[f.pos] < qc) {
                return false;
            }
            if (allMet && f.unitsTaken == qc) {
                return true;
            }
            if (f.pos == f.len) {
                return false;
            }
            // duyệt nhị phân theo thứ tự: nhánh chọn trước; quay lui sẽ thử nhánh bỏ qua
            int ci = f.order[f.pos];
            int[] vec = contrib[ci];
            if (f.unitsTaken + units[ci] <= qc && legalTake(vec, rem)
                    && !conflicts(ctx.keys()[ci])) {
                for (int r = 0; r < rCount; r++) {
                    rem[r] -= vec[r];
                }
                for (String k : ctx.keys()[ci]) {
                    used.add(k);
                }
                f.takePos[f.takeCount++] = f.pos;
                f.selected.add(ci);
                f.unitsTaken += units[ci];
            }
            f.pos++;
        }
    }

    /** Ứng viên chỉ được chọn khi toàn bộ đóng góp của nó không vượt chỉ tiêu còn lại. */
    private static boolean legalTake(int[] vec, int[] rem) {
        for (int r = 0; r < vec.length; r++) {
            if (vec[r] > rem[r]) {
                return false;
            }
        }
        return true;
    }

    private boolean conflicts(String[] keys) {
        for (String k : keys) {
            if (used.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private void undoLastTake(BFrame f) {
        BlockContext ctx = blocks.get(f.b);
        int p = f.takePos[--f.takeCount];
        int ci = f.order[p];
        int[] vec = ctx.contrib()[ci];
        int[] rem = f.rem;
        for (int r = 0; r < vec.length; r++) {
            rem[r] += vec[r];
        }
        for (String k : ctx.keys()[ci]) {
            used.remove(k);
        }
        f.unitsTaken -= ctx.unitsProvided()[ci];
        f.selected.remove(f.selected.size() - 1);
        f.pos = p + 1;
    }

    /** Khung tìm kiếm của một khối: ứng viên hợp lệ lúc tạo khung, chỉ tiêu còn lại, các nhánh đã chọn. */
    private static final class BFrame {
        final int b;
        final int[] order;
        final int len;
        final int[] rem;
        final int[] takePos;
        final List<Integer> selected = new ArrayList<>();
        int[][] suffixMax;
        int[] suffixUnits;
        int pos;
        int takeCount;
        int unitsTaken;

        BFrame(int b, int[] order, int len, int rCount) {
            this.b = b;
            this.order = order;
            this.len = len;
            this.rem = new int[rCount];
            this.takePos = new int[len];
        }
    }
}
