package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.api.ExamOverviewRequest;
import vn.unicom.thi360.exam.domain.AudioRequirement;
import vn.unicom.thi360.exam.domain.ChildCondition;
import vn.unicom.thi360.exam.domain.DisplaySegment;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.ExamStructureOps;
import vn.unicom.thi360.exam.domain.ExamStructureValidator;
import vn.unicom.thi360.exam.domain.ItemShape;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * BE-16 row 4: one structure that carries every new field goes through each stage that rebuilds
 * {@code ExamPart} or {@code ExamSection}, and is asserted after every stage. A stage that rebuilds a record
 * with an old constructor drops the new fields silently, which a save-then-read test never notices.
 */
class ExamStructureFieldPassThroughTest {
    private static final List<DisplaySegment> SEGMENTS = List.of(
            new DisplaySegment("P1", "Phần I", 1, 9, 36, new BigDecimal("50")),
            new DisplaySegment("P2", "Phần II", 10, 15, 6, new BigDecimal("100")));

    private final ExamStructureCodec codec = new ExamStructureCodec(new ObjectMapper());

    @Test
    void everyNewFieldSurvivesEachStage() {
        ExamStructureDocument stored = structure();
        ExamStructureValidator.validate(stored);
        assertIntact(stored, "fixture", true);

        ExamStructureDocument saved = codec.read(codec.writeString(stored));
        assertThat(saved).isEqualTo(stored);
        assertIntact(saved, "save and read", true);

        assertIntact(ExamStructureOps.assignMissingIds(saved), "assignMissingIds", true);
        assertIntact(ExamStructureOps.regenerateIds(saved), "exam clone (regenerateIds)", true);
        assertIntact(ExamStructureOps.restoreTemplateOwned(saved, saved, null), "restore, no template", true);
        assertIntact(ExamStructureOps.restoreTemplateOwned(saved, saved, saved), "restore, template-owned", true);

        assertIntact(ExamStructurePresentation.withPositionRanges(saved), "presentation", true);
    }

    @Test
    void overviewApplyKeepsAudioConditionsAndSegments() {
        ExamStructureDocument stored = structure();

        var duration = ExamOverview.apply(stored, List.of(
                new ExamOverviewRequest.Row("p", null, null, null, null, 90, null)));
        assertIntact(duration, "overview: part duration", true);
        assertThat(duration.parts().getFirst().durationMinutes()).isEqualTo(90);

        var sectionCount = ExamOverview.apply(stored, List.of(
                new ExamOverviewRequest.Row("p", "s1", null, null, 8, null, null)));
        assertIntact(sectionCount, "overview: section row", true);

        // Valid at the starting count (8): segments cover 1..8 and sum to the part's 150 points.
        List<DisplaySegment> eight = List.of(
                new DisplaySegment("P1", "Phần I", 1, 5, 5, new BigDecimal("60")),
                new DisplaySegment("P2", "Phần II", 6, 8, 3, new BigDecimal("90")));
        ExamStructureDocument single = new ExamStructureDocument(List.of(new ExamPart("p", 1, "Phần", null, 8,
                null, null, null, new BigDecimal("150"), List.of(structure().parts().getFirst().sections().getFirst()),
                List.of(), List.of(), null, eight)));
        ExamStructureValidator.validate(single);
        var propagated = ExamOverview.apply(single, List.of(
                new ExamOverviewRequest.Row("p", null, null, null, 9, null, null)));
        assertThat(propagated.parts().getFirst().questionCount()).isEqualTo(9);
        assertThat(propagated.parts().getFirst().sections().getFirst().questionCount()).isEqualTo(9);
        assertIntact(propagated, "overview: propagated count rebuilds the section", false);
        // The overview never rewrites segment ranges; the declared list is carried through unchanged.
        assertThat(propagated.parts().getFirst().displaySegments()).isEqualTo(eight);
    }

    @Test
    void checkInputsKeepTheNewFields() {
        ExamStructureDocument stored = structure();

        var otherSectionMasked = ExamCheckDraftInputs.maskSections(stored, Set.of("s2"));
        assertIntact(otherSectionMasked, "check input: another section masked", true);

        var optionMasked = DefaultExamChecker.maskInvalidOptions(stored, Set.of("opt"));
        assertIntact(optionMasked, "check input: option masked", true);
        assertThat(optionMasked.parts().getFirst().options().getFirst().sections()).isEmpty();

        var maskedSection = ExamCheckDraftInputs.maskSections(stored, Set.of("s1"))
                .parts().getFirst().sections().getFirst();
        assertThat(maskedSection.audioRequirement()).as("masked section keeps its audio condition")
                .isEqualTo(AudioRequirement.REQUIRED);
        assertThat(ExamCheckDraftInputs.maskSections(stored, Set.of("s1")).parts().getFirst().displaySegments())
                .isEqualTo(SEGMENTS);
    }

    @Test
    void previewTargetKeepsTheNewFields() {
        var selection = PreviewSectionTarget.select(structure(), "s1", 8);

        assertIntact(selection.document(), "preview target", true);
        assertThat(selection.document().parts().getFirst().sections()).hasSize(1);
    }

    private static void assertIntact(ExamStructureDocument document, String stage, boolean segments) {
        ExamPart part = document.parts().getFirst();
        ExamSection section = part.sections().getFirst();
        assertThat(section.audioRequirement()).as(stage + ": audioRequirement")
                .isEqualTo(AudioRequirement.REQUIRED);
        QuestionBlock block = section.blocks().getFirst();
        assertThat(block.childConditions()).as(stage + ": child conditions").hasSize(2);
        ChildCondition first = block.childConditions().getFirst();
        assertThat(first.from()).as(stage).isEqualTo(1);
        assertThat(first.to()).as(stage).isEqualTo(2);
        assertThat(first.answerType()).as(stage + ": answerType 1-2").isEqualTo(QuestionType.TRUE_FALSE);
        assertThat(first.itemShape()).as(stage + ": condition itemShape")
                .isEqualTo(new ItemShape(4, null, null, null));
        ChildCondition second = block.childConditions().get(1);
        assertThat(second.answerType()).as(stage + ": answerType 3-8").isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(second.itemShape().options()).as(stage + ": condition options").isEqualTo(4);
        assertThat(block.itemShape().options()).as(stage + ": block itemShape.options").isEqualTo(4);
        if (part.sections().size() > 1) {
            assertThat(part.sections().get(1).audioRequirement()).as(stage + ": second section")
                    .isEqualTo(AudioRequirement.FORBIDDEN);
        }
        if (segments) {
            assertThat(part.displaySegments()).as(stage + ": displaySegments").isEqualTo(SEGMENTS);
        }
    }

    private static ExamStructureDocument structure() {
        QuestionBlock set = new QuestionBlock("b1", "Bài đọc dùng chung", QuestionType.QUESTION_SET, null, 1, 8,
                null, null, null, null, false, new BigDecimal("6"), List.of(), null, true, false, List.of(),
                List.of(new ChildCondition(1, 2, List.of(), QuestionType.TRUE_FALSE, new ItemShape(4, null, null)),
                        new ChildCondition(3, 8, List.of(), QuestionType.SINGLE_CHOICE,
                                new ItemShape(null, null, null, 4))),
                new ItemShape(null, null, null, 4), List.of(), false, List.of(), null);
        QuestionBlock single = new QuestionBlock("b2", "Câu đơn", QuestionType.SINGLE_CHOICE, 7, null, null,
                null, null, null, null, false, null, List.of(), null, true, false, List.of(), List.of(), null,
                List.of(), false, List.of(), null);
        QuestionBlock optionBlock = new QuestionBlock("b3", "Câu của tùy chọn", QuestionType.SINGLE_CHOICE, 1, null,
                null, null, null, null, null, false, null, List.of(), null, true, false, List.of(), List.of(), null,
                List.of(), false, List.of(), null);
        ExamSection listening = new ExamSection("s1", "ANH", 8, null, null, null, null, List.of(set), List.of(),
                AudioRequirement.REQUIRED);
        ExamSection reading = new ExamSection("s2", "ANH", 7, null, null, null, null, List.of(single), List.of(),
                AudioRequirement.FORBIDDEN);
        ExamPartOption option = new ExamPartOption("opt", OptionKind.SUBJECT, "ANH", "Tùy chọn", null, null,
                List.of(new ExamSection("s3", "ANH", 1, null, null, null, null, List.of(optionBlock), List.of(),
                        AudioRequirement.REQUIRED)), List.of(), true);
        return new ExamStructureDocument(List.of(new ExamPart("p", 1, "Phần", 60, 15, null, null, null,
                new BigDecimal("150"), List.of(listening, reading), List.of(option), List.of(), null, SEGMENTS)));
    }
}
