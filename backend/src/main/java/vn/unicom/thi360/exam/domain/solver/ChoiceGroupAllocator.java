package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Phân bổ HSA theo nhóm môn tự nhiên/xã hội theo B10. */
public final class ChoiceGroupAllocator implements SubjectCountAllocator {
    @Override
    public List<int[]> allocations(SubjectChoice choice, List<SectionOnPath> picked) {
        if (picked.stream().allMatch(s -> s.choiceGroup() == null)) {
            return List.of(choice.countsByPick().stream().mapToInt(Integer::intValue).toArray());
        }
        if (picked.size() != 3 || picked.stream().anyMatch(s ->
                !"NATURAL".equals(s.choiceGroup()) && !"SOCIAL".equals(s.choiceGroup()))) {
            throw new IllegalArgumentException("choiceGroup must be NATURAL or SOCIAL");
        }
        int high = choice.countsByPick().stream().mapToInt(Integer::intValue).max().orElseThrow();
        int low = choice.countsByPick().stream().mapToInt(Integer::intValue).min().orElseThrow();
        long natural = picked.stream().filter(s -> "NATURAL".equals(s.choiceGroup())).count();
        List<int[]> result = new ArrayList<>();
        if (natural == 0 || natural == 3) {
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
