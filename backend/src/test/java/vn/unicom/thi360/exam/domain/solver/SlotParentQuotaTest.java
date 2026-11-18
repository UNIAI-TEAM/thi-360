package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SlotParentQuotaTest {
    @Test
    void matrixAndCompetenciesApplyAcrossSlotsNotToEachSlotOrOtherBlocks() {
        SlotParentQuota parent = new SlotParentQuota("parent",
                new MatrixQuota.Separate(List.of(new DimensionQuota("G1", 1),
                        new DimensionQuota("G2", 1)), List.of()),
                List.of(new CompetencyQuota("C", 1)), List.of());
        GroupQuota slot1 = slot("slot1", parent);
        GroupQuota slot2 = slot("slot2", parent);
        GroupQuota outside = GroupQuota.single("outside", 1);
        SectionOnPath section = new SectionOnPath("S", "S", 3, SubjectQuota.NONE,
                List.of(slot1, slot2, outside));
        SolvePath path = SolvePath.of(0, List.of(section));
        CandidateSource valid = group -> switch (group.groupId()) {
            case "slot1" -> List.of(candidate("one", "G1", true));
            case "slot2" -> List.of(candidate("two", "G2", false));
            default -> List.of(PathCandidate.standalone("outside", 1, "SINGLE_CHOICE", "G3", null, null));
        };
        PaperSolver solver = new PaperSolver();
        assertThat(solver.solvePath(path, valid, SolveMode.check()).status()).isEqualTo(SolveStatus.VALID);
        CandidateSource wrong = group -> group.groupId().equals("slot2")
                ? List.of(candidate("two", "G1", false)) : valid.candidatesFor(group);
        assertThat(solver.solvePath(path, wrong, SolveMode.check()).status()).isEqualTo(SolveStatus.INVALID);
        CandidateSource missingCompetency = group -> group.groupId().equals("slot1")
                ? List.of(candidate("one", "G1", false)) : valid.candidatesFor(group);
        assertThat(solver.solvePath(path, missingCompetency, SolveMode.check()).status()).isEqualTo(SolveStatus.INVALID);
    }

    private static GroupQuota slot(String id, SlotParentQuota parent) {
        return new GroupQuota(id, GroupQuota.GroupKind.SET, null, null, 1, 1, null,
                null, List.of(), List.of(), null, null, parent);
    }

    private static PathCandidate candidate(String id, String grade, boolean competency) {
        return new PathCandidate(id, 1, id, null, List.of(new CountingUnit(id + "-child",
                "SINGLE_CHOICE", grade, null, List.of(), competency ? List.of("C") : List.of(), null)));
    }
}
