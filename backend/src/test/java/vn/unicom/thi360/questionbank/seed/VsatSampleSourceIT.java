package vn.unicom.thi360.questionbank.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.unicom.thi360.questionbank.spi.QuestionType.CLOZE;
import static vn.unicom.thi360.questionbank.spi.QuestionType.MATCHING;
import static vn.unicom.thi360.questionbank.spi.QuestionType.QUESTION_SET;
import static vn.unicom.thi360.questionbank.spi.QuestionType.SHORT_ANSWER;
import static vn.unicom.thi360.questionbank.spi.QuestionType.SINGLE_CHOICE;
import static vn.unicom.thi360.questionbank.spi.QuestionType.TRUE_FALSE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.ChildCondition;

/**
 * BE-19 (UNI-2691) plan rows 1 and 2, V-SAT half: one case per line of spec sections 5.1 and 5.2,
 * plus ADV:BE-19:3 (each Dia li part II slot finds exactly its two sets), through {@code QuestionSourcePort}.
 */
class VsatSampleSourceIT extends SampleSourceSupport {

    // ---- spec 5.1: six subjects with Part IV ------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"TOAN", "LY", "HOA", "SINH", "SU", "DIA", "VAN"})
    void spec51PartITrueFalseFourStatementsSixPointsNine(String subject) {
        var filter = filter(subject, Block.of(TRUE_FALSE).points("6").shape(FOUR_STATEMENTS));
        assertUnits("5.1 part I " + subject, filter, 9);
    }

    /** Part II, two sets of three single-choice children; Dia li adds its two slot labels below. */
    @ParameterizedTest
    @ValueSource(strings = {"TOAN", "LY", "HOA", "SINH", "SU", "DIA"})
    void spec51PartIITwoSetsOfThreeSixPoints(String subject) {
        assertSets("5.1 part II " + subject, filter(subject, partTwoSet()), 2, 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOAN", "LY", "HOA", "SINH", "SU", "DIA", "VAN"})
    void spec51PartIIIMatchingFourItemsSixPointsFive(String subject) {
        var filter = filter(subject, Block.of(MATCHING).points("6").shape(FOUR_LEFT_ITEMS));
        assertUnits("5.1 part III " + subject, filter, 5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOAN", "LY", "HOA", "SINH", "SU", "DIA"})
    void spec51PartIVShortAnswerSixPointsFive(String subject) {
        assertUnits("5.1 part IV " + subject, filter(subject, Block.of(SHORT_ANSWER).points("6")), 5);
    }

    // ---- spec 5.1: Ngu van I-III, part II is one poetry set and one prose set ---------------

    @Test
    void spec51LiteraturePartIIPoetrySetOfThree() {
        assertSets("5.1 VAN part II poetry", filter("VAN", partTwoSet().tag("VAN_THO")), 1, 3);
    }

    @Test
    void spec51LiteraturePartIIProseSetOfThree() {
        assertSets("5.1 VAN part II prose", filter("VAN", partTwoSet().tag("VAN_TRUYEN")), 1, 3);
    }

    // ---- ADV:BE-19:3: Dia li part II slots match by root tag, one label per set -------------

    @Test
    void geographySlotBangSoLieuFindsExactlyItsTwoSets() {
        assertThat(slotSets("DIA_BANG_SO_LIEU")).hasSize(2);
    }

    @Test
    void geographySlotBieuDoFindsExactlyItsTwoSets() {
        assertThat(slotSets("DIA_BIEU_DO")).hasSize(2);
    }

    @Test
    void geographySlotsShareNoSetAndTogetherCoverTheFourSeededSets() {
        Set<UUID> bang = slotSets("DIA_BANG_SO_LIEU");
        Set<UUID> bieu = slotSets("DIA_BIEU_DO");
        assertThat(bang).doesNotContainAnyElementsOf(bieu);
        Set<UUID> all = new HashSet<>(bang);
        all.addAll(bieu);
        Set<UUID> seeded = new HashSet<>();
        candidates(filter("DIA", partTwoSet())).forEach(c -> seeded.add(c.questionId()));
        assertThat(all).isEqualTo(seeded).hasSize(4);
    }

    private Set<UUID> slotSets(String label) {
        List<Candidate> sets = candidates(filter("DIA", partTwoSet().slot(label)));
        assertThat(sets).as(label).allMatch(c -> c.units().size() == 3);
        Set<UUID> ids = new HashSet<>();
        sets.forEach(c -> ids.add(c.questionId()));
        return ids;
    }

    private static Block partTwoSet() {
        return Block.of(QUESTION_SET).childType(SINGLE_CHOICE).points("6");
    }

    // ---- spec 5.2: Tieng Anh FULL ----------------------------------------------------------

    @Test
    void spec52IndependentTrueFalseFourStatementsSixPointsSeven() {
        var filter = filter("ANH", Block.of(TRUE_FALSE).points("6").shape(FOUR_STATEMENTS));
        assertUnits("5.2 true/false", filter, 7);
    }

    /** One shared passage of eight: positions 1-2 true/false with four statements, 3-8 single choice. */
    @Test
    void spec52SharedReadingOneSetOfEightTwoTrueFalseThenSixSingleChoice() {
        var block = Block.of(QUESTION_SET).points("6").children(
                new ChildCondition(1, 2, List.of(), TRUE_FALSE, FOUR_STATEMENTS),
                new ChildCondition(3, 8, List.of(), SINGLE_CHOICE, null));
        var sets = assertSets("5.2 shared reading", filter("ANH", block), 1, 8);
        assertThat(sets).allMatch(c -> c.code().startsWith("SEED-VSAT-ANH-MIXED-SET-"));
        // 2 x 4 + 6 x 1 = 14 commands (spec 5.2 rule 5).
        for (Candidate set : sets) {
            assertThat(set.units().stream().mapToInt(u -> u.commandCount()).sum()).as(set.code()).isEqualTo(14);
        }
        // The six-child single-choice sets of the same subject are not a shared reading of eight.
        assertThat(candidates(filter("ANH", block))).noneMatch(c -> c.code().startsWith("SEED-VSAT-ANH-CHOICE-SET-"));
    }

    @Test
    void spec52MatchingFourItemsSixPointsFive() {
        var filter = filter("ANH", Block.of(MATCHING).points("6").shape(FOUR_LEFT_ITEMS));
        assertUnits("5.2 matching", filter, 5);
    }

    @Test
    void spec52ClozeOnePassageFivePositionsSixPoints() {
        assertSets("5.2 cloze", filter("ANH", Block.of(CLOZE).points("6")), 1, 5);
    }
}
