package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void nullGroupActsAsOneSharedGroup() {
        // môn không khai báo choiceGroup: một nhóm chung, mọi môn một lượt nhận low (k bất kỳ)
        SectionOnPath cs0 = section("CS0", null);
        SectionOnPath cs1 = section("CS1", null);
        SectionOnPath cs2 = section("CS2", null);
        SubjectChoice choice = new SubjectChoice("opt-sc", List.of(cs0, cs1, cs2), 2, List.of(2, 1));
        ChoiceGroupAllocator allocator = new ChoiceGroupAllocator();
        List<int[]> all = allocator.allocations(choice, List.of(cs0, cs2));
        assertThat(all).hasSize(2);
        assertThat(all.get(0)).containsExactly(1, 2);
        assertThat(all.get(1)).containsExactly(2, 1);
        assertThat(allocator.allocations(choice, List.of(cs0, cs1, cs2))).hasSize(3);
        assertThat(allocator.select(choice, List.of(cs0, cs2), 7))
                .containsExactly(allocator.select(choice, List.of(cs0, cs2), 7));
    }

    @Test
    void nullMixedWithNamedGroupRejected() {
        SectionOnPath cs0 = section("CS0", null);
        SectionOnPath phy = section("PHY", "NATURAL");
        SubjectChoice choice = new SubjectChoice("opt-mix", List.of(cs0, phy), 2, List.of(2, 1));
        ChoiceGroupAllocator allocator = new ChoiceGroupAllocator();
        assertThatThrownBy(() -> allocator.allocations(choice, List.of(cs0, phy)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mixedNamedGroupsStillNeedThree() {
        SectionOnPath phy = section("PHY", "NATURAL");
        SectionOnPath hist = section("HIST", "SOCIAL");
        SubjectChoice choice = new SubjectChoice("opt-2", List.of(phy, hist), 2, List.of(2, 1));
        ChoiceGroupAllocator allocator = new ChoiceGroupAllocator();
        assertThatThrownBy(() -> allocator.allocations(choice, List.of(phy, hist)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SectionOnPath section(String code, String group) {
        return new SectionOnPath(code, code, null, SubjectQuota.NONE,
                List.of(GroupQuota.remainder(code)), group);
    }
}
