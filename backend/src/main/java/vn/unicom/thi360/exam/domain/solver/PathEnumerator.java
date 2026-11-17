package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Liệt kê đường lựa chọn theo kiểu lặp (odometer) trên các phần CHOICE — không dựng cả tích vào
 * bộ nhớ (spec 5.4).
 *
 * <p>Một đường gồm: mọi môn của phần FIXED + mọi môn chung của phần CHOICE + tổ hợp
 * {@code choose} phương án của mỗi phần CHOICE. Giới hạn 200 đường đếm theo <b>trọng số</b>:
 * phương án {@link SolveOption.Sections} tính một đường, phương án {@link SubjectChoice} tính bằng
 * số cách chọn môn của nó. Trọng số của tổ hợp là tích trọng số của các phương án đã chọn;
 * mỗi đường mang toàn bộ cách chọn môn, VALID chỉ khi mọi cách đều VALID (spec 5.4).
 */
final class PathEnumerator {

    private final List<SolvePart> parts;
    private final SubjectCountAllocator allocator;
    private final Runnable deadlineCheck;
    private final List<SolvePart.Choice> choiceParts = new ArrayList<>();
    private final List<Long> choiceCombinations = new ArrayList<>();
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
                if (c.choose() > c.options().size()) {
                    empty = true;
                    break;
                }
                choiceParts.add(c);
                long combinations = combinations(c.options().size(), c.choose());
                choiceCombinations.add(combinations);
                combos = multiplyCapped(combos, combinations);
                weighted = multiplyCapped(weighted, weightedCombinations(c.options(), c.choose()));
            }
        }
        this.comboCount = empty ? 0 : combos;
        this.weightedCount = empty ? 0 : weighted;
    }

    /** Trọng số đường của một phương án: SubjectChoice = số cách chọn môn, Sections = 1. */
    private static long weight(SolveOption option) {
        return option instanceof SubjectChoice sc ? sc.pickCount() : 1;
    }

    /** Tổng tích trọng số của mọi tổ hợp k phần tử, tính quy hoạch động không dựng tổ hợp. */
    private long weightedCombinations(List<SolveOption> options, int choose) {
        long[] counts = new long[choose + 1];
        counts[0] = 1;
        int seen = 0;
        for (SolveOption option : options) {
            deadlineCheck.run();
            seen++;
            long weight = weight(option);
            for (int k = Math.min(choose, seen); k >= 1; k--) {
                deadlineCheck.run();
                counts[k] = addCapped(counts[k], multiplyCapped(counts[k - 1], weight));
            }
        }
        return counts[choose];
    }

    private long combinations(int n, int choose) {
        if (choose < 0 || choose > n) {
            return 0;
        }
        if (choose == 0 || choose == n) {
            return 1;
        }
        if (choose == 1 || choose == n - 1) {
            return n;
        }
        int width = Math.min(choose, n - choose);
        long[] counts = new long[width + 1];
        counts[0] = 1;
        for (int i = 1; i <= n; i++) {
            deadlineCheck.run();
            for (int k = Math.min(i, width); k >= 1; k--) {
                if ((k & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                counts[k] = addCapped(counts[k], counts[k - 1]);
            }
        }
        return counts[width];
    }

    private static long addCapped(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
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
     * Đường-kết-quả thứ {@code comboIndex} (0-based): tổ hợp theo thứ tự từ điển của từng phần;
     * phương án SUBJECT_CHOICE mang toàn bộ cách chọn môn.
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
                long optionRank = rem / stride;
                rem %= stride;
                int nextIndex = 0;
                for (int selected = 0; selected < c.choose(); selected++) {
                    for (int index = nextIndex; index < c.options().size(); index++) {
                        deadlineCheck.run();
                        long tail = combinations(c.options().size() - index - 1,
                                c.choose() - selected - 1);
                        if (optionRank >= tail) {
                            optionRank -= tail;
                            continue;
                        }
                        SolveOption opt = c.options().get(index);
                        if (opt instanceof SolveOption.Sections s) {
                            chosen.add(new ChosenOption(c.partId(), s.optionId()));
                            sections.addAll(s.sections());
                        } else {
                            SubjectChoice sc = (SubjectChoice) opt;
                            chosen.add(new ChosenOption(c.partId(), sc.optionId()));
                            choices.add(sc);
                            picks.add(sc.picks(allocator));
                        }
                        nextIndex = index + 1;
                        break;
                    }
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
            stride = multiplyCapped(stride, choiceCombinations.get(j));
        }
        return stride;
    }

    private static long multiplyCapped(long a, long b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }
}
