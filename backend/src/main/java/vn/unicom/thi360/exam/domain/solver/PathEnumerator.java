package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.List;

/**
 * Liệt kê đường lựa chọn theo kiểu lặp (odometer) trên các phần CHOICE — không dựng cả tích vào bộ nhớ
 * (spec 5.4).
 *
 * <p>Một đường gồm: mọi khối của phần FIXED + mọi khối chung của phần CHOICE + các khối của một lựa chọn
 * cho mỗi phần CHOICE. Thứ tự liệt kê ổn định: lựa chọn của phần CHOICE cuối cùng biến thiên nhanh nhất.
 * Phần CHOICE không có lựa chọn nào làm tập đường rỗng.
 */
final class PathEnumerator {

    private final List<SolvePart> parts;
    private final List<SolvePart.Choice> choiceParts = new ArrayList<>();
    private final long total;

    PathEnumerator(List<SolvePart> parts) {
        this.parts = parts == null ? List.of() : List.copyOf(parts);
        long t = 1;
        boolean empty = false;
        for (SolvePart p : this.parts) {
            if (p instanceof SolvePart.Choice c) {
                if (c.options().isEmpty()) {
                    empty = true;
                    break;
                }
                choiceParts.add(c);
                t = multiplyCapped(t, c.options().size());
            }
        }
        this.total = empty ? 0 : t;
    }

    /** Số đường (đã cắt trần ở {@link Long#MAX_VALUE}). */
    long total() {
        return total;
    }

    /** Đường thứ {@code index} (0-based); gọi tuần tự hay ngẫu nhiên đều được. */
    SolvePath pathAt(long index) {
        List<ChosenOption> chosen = new ArrayList<>(choiceParts.size());
        List<BlockQuota> blocks = new ArrayList<>();
        long rem = index;
        int ci = 0;
        for (SolvePart p : parts) {
            if (p instanceof SolvePart.Fixed f) {
                blocks.addAll(f.blocks());
            } else {
                SolvePart.Choice c = (SolvePart.Choice) p;
                blocks.addAll(c.commonBlocks());
                long stride = strideAfter(ci);
                int optIdx = (int) Math.min((rem / stride), c.options().size() - 1L);
                rem = rem % stride;
                SolveOption opt = c.options().get(optIdx);
                chosen.add(new ChosenOption(c.partId(), opt.code()));
                blocks.addAll(opt.blocks());
                ci++;
            }
        }
        return new SolvePath(index, chosen, blocks);
    }

    /** Tích số lựa chọn của các phần CHOICE đứng sau phần thứ {@code ci} (đã cắt trần). */
    private long strideAfter(int ci) {
        long stride = 1;
        for (int j = ci + 1; j < choiceParts.size(); j++) {
            stride = multiplyCapped(stride, choiceParts.get(j).options().size());
        }
        return stride;
    }

    private static long multiplyCapped(long a, long b) {
        if (a > Long.MAX_VALUE / Math.max(b, 1)) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }
}
