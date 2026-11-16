package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Quy tắc tạm D25 của {@link StructureOrderAllocator}: các môn được chọn xếp theo thứ tự cấu trúc,
 * môn thứ i nhận {@code countsByPick[i]} — không theo thứ tự học viên bấm chọn.
 */
class StructureOrderAllocatorTest {

    private final StructureOrderAllocator allocator = new StructureOrderAllocator();

    private SubjectChoice choice(int choose, int... values) {
        List<SectionOnPath> sections = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> SolverTestFixtures.choiceSection("S" + i, SubjectQuota.NONE))
                .toList();
        return new SubjectChoice("OPT", sections, choose,
                java.util.Arrays.stream(values).boxed().toList());
    }

    @Test
    @DisplayName("5 chọn 3 với countsByPick 17-17-16: môn đứng sau trong cấu trúc nhận 16")
    void hsaCountsByPick() {
        SubjectChoice c = choice(3, 17, 17, 16);
        // chọn môn 1, 3, 4 (thứ tự cấu trúc): 17, 17, 16 — không theo thứ tự bấm chọn
        assertThat(allocator.allocate(c, List.of("S1", "S3", "S4"))).containsExactly(17, 17, 16);
        // bộ ba đầu tiên cũng vậy
        assertThat(allocator.allocate(c, List.of("S0", "S1", "S2"))).containsExactly(17, 17, 16);
    }

    @Test
    @DisplayName("thứ tự bấm chọn của học viên không đổi kết quả gán")
    void clickOrderDoesNotMatter() {
        SubjectChoice c = choice(2, 10, 20);
        assertThat(allocator.allocate(c, List.of("S2", "S0"))).containsExactly(10, 20);
    }
}
