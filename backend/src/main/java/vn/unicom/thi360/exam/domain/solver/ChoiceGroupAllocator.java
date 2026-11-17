package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Phân bổ HSA theo nhóm môn tự nhiên/xã hội theo B10; nhóm null tính là một nhóm chung. */
public final class ChoiceGroupAllocator implements SubjectCountAllocator {
    @Override
    public List<int[]> allocations(SubjectChoice choice, List<SectionOnPath> picked) {
        long nulls = picked.stream().filter(s -> s.choiceGroup() == null).count();
        if (nulls > 0 && nulls < picked.size()) {
            throw new IllegalArgumentException("choiceGroup must not mix null and non-null");
        }
        if (picked.stream().anyMatch(s -> s.choiceGroup() != null
                && !"NATURAL".equals(s.choiceGroup()) && !"SOCIAL".equals(s.choiceGroup()))) {
            throw new IllegalArgumentException("choiceGroup must be NATURAL or SOCIAL");
        }
        long natural = picked.stream().filter(s -> "NATURAL".equals(s.choiceGroup())).count();
        long social = picked.size() - nulls - natural;
        if (natural > 0 && social > 0 && picked.size() != 3) {
            throw new IllegalArgumentException("mixed NATURAL/SOCIAL needs exactly 3 subjects");
        }
        int high = choice.countsByPick().stream().mapToInt(Integer::intValue).max().orElseThrow();
        int low = choice.countsByPick().stream().mapToInt(Integer::intValue).min().orElseThrow();
        List<int[]> result = new ArrayList<>();
        if (natural == 0 || social == 0) {
            // một nhóm chung (toàn NATURAL, toàn SOCIAL, hoặc toàn null): mỗi môn một lượt nhận low
            for (int i = 0; i < picked.size(); i++) {
                int[] counts = new int[picked.size()];
                Arrays.fill(counts, high);
                counts[i] = low;
                result.add(counts);
            }
        } else {
            String minority = natural == 1 ? "NATURAL" : "SOCIAL";
            int[] counts = new int[picked.size()];
            for (int i = 0; i < picked.size(); i++) {
                counts[i] = minority.equals(picked.get(i).choiceGroup()) ? low : high;
            }
            result.add(counts);
        }
        return result;
    }
}
