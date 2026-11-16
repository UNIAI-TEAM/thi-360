package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Liệt kê đường lựa chọn theo kiểu lặp (odometer) trên các phần CHOICE — không dựng cả tích vào
 * bộ nhớ (spec 5.4).
 *
 * <p>Một đường gồm: mọi môn của phần FIXED + mọi môn chung của phần CHOICE + một phương án cho
 * mỗi phần CHOICE. Giới hạn 200 đường đếm theo <b>trọng số</b>: phương án
 * {@link SolveOption.Sections} tính một đường, phương án {@link SubjectChoice} tính bằng số cách
 * chọn môn của nó. Việc liệt kê đường-kết-quả đi theo tích phương án; một đường mang phương án
 * SUBJECT_CHOICE mang toàn bộ cách chọn môn của nó ({@link SolvePath#picks()}) — kết quả tổng hợp
 * trên từng cách (VALID khi mọi cách VALID, spec 5.4).
 */
final class PathEnumerator {

    private final List<SolvePart> parts;
    private final SubjectCountAllocator allocator;
    private final Runnable deadlineCheck;
    private final List<SolvePart.Choice> choiceParts = new ArrayList<>();
    private final long comboCount; // tích số phương án (số đường-kết-quả)
    private final long weightedCount; // tổng trọng số (mỗi cách chọn môn = 1)

    PathEnumerator(List<SolvePart> parts, SubjectCountAllocator allocator,
            Runnable deadlineCheck) {
        this.parts = parts == null ? List.of() : List.copyOf(parts);
        this.allocator = Objects.requireNonNull(allocator);
        this.deadlineCheck = deadlineCheck;
        long combos = 1;
        long weighted = 1;
        boolean empty = false;
        for (SolvePart p : this.parts) {
            deadlineCheck.run();
            if (p instanceof SolvePart.Choice c) {
                if (c.options().isEmpty()) {
                    empty = true;
                    break;
                }
                choiceParts.add(c);
                combos = multiplyCapped(combos, c.options().size());
                weighted = multiplyCapped(weighted, weightSum(c.options()));
            }
        }
        this.comboCount = empty ? 0 : combos;
        this.weightedCount = empty ? 0 : weighted;
    }

    /** Trọng số đường của một phương án: SubjectChoice = số cách chọn môn, Sections = 1. */
    private static long weight(SolveOption option) {
        return option instanceof SubjectChoice sc ? sc.pickCount() : 1;
    }

    private static long weightSum(List<SolveOption> options) {
        long sum = 0;
        for (SolveOption o : options) {
            sum += weight(o);
            if (sum >= Long.MAX_VALUE / 2) {
                return Long.MAX_VALUE;
            }
        }
        return sum;
    }

    /** Số đường đếm cho giới hạn (đã cắt trần): mỗi cách chọn môn của SUBJECT_CHOICE tính một. */
    long total() {
        return weightedCount;
    }

    /** Số đường-kết-quả = tích số phương án (đã cắt trần). */
    long combos() {
        return comboCount;
    }

    /**
     * Đường-kết-quả thứ {@code comboIndex} (0-based): một phương án của mỗi phần CHOICE; phương
     * án SUBJECT_CHOICE mang toàn bộ cách chọn môn.
     */
    SolvePath pathAt(long comboIndex) {
        deadlineCheck.run();
        List<ChosenOption> chosen = new ArrayList<>(choiceParts.size());
        List<SectionOnPath> sections = new ArrayList<>();
        List<SubjectChoice> choices = new ArrayList<>();
        List<List<SubjectPick>> picks = new ArrayList<>();
        long rem = comboIndex;
        int ci = 0;
        for (SolvePart p : parts) {
            if (p instanceof SolvePart.Fixed f) {
                sections.addAll(f.sections());
            } else {
                SolvePart.Choice c = (SolvePart.Choice) p;
                sections.addAll(c.commonSections());
                long stride = strideAfter(ci);
                int optIdx = (int) Math.min(rem / stride, (long) c.options().size() - 1L);
                rem = rem % stride;
                SolveOption opt = c.options().get(optIdx);
                if (opt instanceof SolveOption.Sections s) {
                    chosen.add(new ChosenOption(c.partId(), s.optionId()));
                    sections.addAll(s.sections());
                } else {
                    SubjectChoice sc = (SubjectChoice) opt;
                    chosen.add(new ChosenOption(c.partId(), sc.optionId()));
                    choices.add(sc);
                    picks.add(sc.picks(allocator));
                }
                ci++;
            }
        }
        return new SolvePath(comboIndex, chosen, sections, choices, picks);
    }

    /** Tích số phương án của các phần CHOICE đứng sau phần thứ {@code ci} (đã cắt trần). */
    private long strideAfter(int ci) {
        long stride = 1;
        for (int j = ci + 1; j < choiceParts.size(); j++) {
            stride = multiplyCapped(stride, choiceParts.get(j).options().size());
        }
        return stride;
    }

    private static long multiplyCapped(long a, long b) {
        if (b <= 0) {
            return a;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }
}
