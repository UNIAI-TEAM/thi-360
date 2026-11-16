package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChoiceGroupAllocatorRegressionTest {
    @Test
    void scienceGroupSplits() {
        SectionOnPath physics = section("PHY", "NATURAL");
        SectionOnPath chemistry = section("CHEM", "NATURAL");
        SectionOnPath biology = section("BIO", "NATURAL");
        SectionOnPath history = section("HIST", "SOCIAL");
        SectionOnPath geography = section("GEO", "SOCIAL");
        SubjectChoice choice = new SubjectChoice("science", List.of(physics, chemistry, biology,
                history, geography), 3, List.of(17, 17, 16));
        ChoiceGroupAllocator allocator = new ChoiceGroupAllocator();
        assertThat(allocator.allocations(choice, List.of(physics, chemistry, history)).get(0))
                .containsExactly(17, 17, 16);
        assertThat(allocator.allocations(choice, List.of(biology, history, geography)).get(0))
                .containsExactly(16, 17, 17);
        List<int[]> allNatural = allocator.allocations(choice, List.of(physics, chemistry, biology));
        assertThat(allNatural).hasSize(3);
        assertThat(allNatural.get(0)).containsExactly(16, 17, 17);
        assertThat(allNatural.get(1)).containsExactly(17, 16, 17);
        assertThat(allNatural.get(2)).containsExactly(17, 17, 16);
        assertThat(choice.picks(allocator)).hasSize(12);
        assertThat(allocator.select(choice, List.of(physics, chemistry, biology), 42))
                .containsExactly(allocator.select(choice, List.of(physics, chemistry, biology), 42));
    }

    private static SectionOnPath section(String code, String group) {
        return new SectionOnPath(code, code, null, SubjectQuota.NONE,
                List.of(GroupQuota.remainder(code)), group);
    }
}
