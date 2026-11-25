package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.PositionRange;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class ExamStructurePresentationTest {
    @Test
    void alternativesStartAfterTheCommonBlocksRatherThanAfterEachOther() {
        ExamSection common = section("common", block("common-block", 2));
        ExamPartOption first = option("first", section("s1", block("b1", 3)));
        ExamPartOption second = option("second", section("s2", block("b2", 4)));
        ExamPart part = new ExamPart("p", 1, "part", null, null, null, null, null, null,
                List.of(common), List.of(first, second));
        var presented = ExamStructurePresentation.withPositionRanges(new ExamStructureDocument(List.of(part)));
        var options = presented.parts().getFirst().options();
        assertThat(options.get(0).sections().getFirst().blocks().getFirst().positionRange())
                .isEqualTo(new PositionRange(3, 5));
        assertThat(options.get(1).sections().getFirst().blocks().getFirst().positionRange())
                .isEqualTo(new PositionRange(3, 6));
        assertThat(common.blocks().getFirst().positionRange()).isNull();
    }

    @Test
    void mixedBlocksOmitFixedBlockRangesWhileAdvancingSubsequentSections() {
        ExamSection mixed = new ExamSection("mixed", "MATH", 5, null, null, null, null,
                List.of(block("b1", 3), block("b2", 2)), List.of(), null, null, null, true);
        ExamSection following = section("following", block("b3", 4));
        ExamPart part = new ExamPart("p", 1, "part", null, null, null, null, null, null,
                List.of(mixed, following), List.of());
        var presented = ExamStructurePresentation.withPositionRanges(new ExamStructureDocument(List.of(part)));
        var sections = presented.parts().getFirst().sections();
        assertThat(sections.get(0).isMixBlocks()).isTrue();
        assertThat(sections.get(0).blocks()).extracting(QuestionBlock::positionRange).containsOnlyNulls();
        assertThat(sections.get(1).blocks().getFirst().positionRange()).isEqualTo(new PositionRange(6, 9));
    }

    private static ExamSection section(String id, QuestionBlock block) {
        return new ExamSection(id, "MATH", null, null, null, null, null, List.of(block));
    }

    private static ExamPartOption option(String id, ExamSection section) {
        return new ExamPartOption(id, OptionKind.BRANCH, id, id, null, null, List.of(section), List.of(), true);
    }

    private static QuestionBlock block(String id, int count) {
        return new QuestionBlock(id, id, QuestionType.SINGLE_CHOICE, count, null, null, null, null,
                null, null, false, null, List.of(), null, true, false, List.of(), List.of(), null,
                List.of(), false, List.of(), null);
    }
}
