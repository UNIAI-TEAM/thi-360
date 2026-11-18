package vn.unicom.thi360.questionbank.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.unicom.thi360.questionbank.spi.QuestionType.MULTIPLE_CHOICE;
import static vn.unicom.thi360.questionbank.spi.QuestionType.QUESTION_SET;
import static vn.unicom.thi360.questionbank.spi.QuestionType.SHORT_ANSWER;
import static vn.unicom.thi360.questionbank.spi.QuestionType.SINGLE_CHOICE;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.AudioRequirement;

/**
 * BE-19 (UNI-2691) plan rows 1 and 2, H-SCA half: one case per line of spec sections 4.1 to 4.3,
 * asked through {@code QuestionSourcePort} with exactly that line's filters.
 */
class HscaSampleSourceIT extends SampleSourceSupport {

    // ---- spec 4.1: Toan, Vat li, Hoa hoc, Sinh hoc ------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"TOAN", "LY", "HOA", "SINH"})
    void spec41Line1SingleChoiceFourOptionsQuarterPointTwenty(String subject) {
        var filter = filter(subject, Block.of(SINGLE_CHOICE).points("0.25").shape(FOUR_OPTIONS));
        assertUnits("4.1 line 1 " + subject, filter, 20);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOAN", "LY", "HOA", "SINH"})
    void spec41Line2MultipleChoiceQuarterPointFive(String subject) {
        var filter = filter(subject, Block.of(MULTIPLE_CHOICE).points("0.25"));
        assertUnits("4.1 line 2 " + subject, filter, 5);
    }

    /** Part 2: one flexible set block (no setCount, no setSize), five children, so a selection can reach 5. */
    @ParameterizedTest
    @ValueSource(strings = {"TOAN", "LY", "HOA", "SINH"})
    void spec41Line3DataSetFlexibleFiveChildrenQuarterPoint(String subject) {
        var filter = filter(subject, Block.of(QUESTION_SET).points("0.25"));
        List<Integer> sizes = candidates(filter).stream().map(c -> c.units().size()).toList();
        assertThat(sizes).as("4.1 line 3 " + subject).isNotEmpty();
        assertThat(composable(sizes, 5)).as("4.1 line 3 " + subject + " sizes " + sizes).isTrue();
        assertUnits("4.1 line 3 " + subject, filter, 5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOAN", "LY", "HOA", "SINH"})
    void spec41Line4ShortAnswerQuarterPointTen(String subject) {
        var filter = filter(subject, Block.of(SHORT_ANSWER).points("0.25"));
        assertUnits("4.1 line 4 " + subject, filter, 10);
    }

    // ---- spec 4.2: Ngu van, one set per line, single choice with four options ---------------

    @Test
    void spec42Line1InformationTextSetOfFive() {
        assertSets("4.2 line 1", filter("VAN", literature("VAN_THONG_TIN")), 1, 5);
    }

    @Test
    void spec42Line2ArgumentTextSetOfSeven() {
        assertSets("4.2 line 2", filter("VAN", literature("VAN_NGHI_LUAN")), 1, 7);
    }

    @Test
    void spec42Line3PoetrySetOfEight() {
        assertSets("4.2 line 3", filter("VAN", literature("VAN_THO")), 1, 8);
    }

    private static Block literature(String tag) {
        return Block.of(QUESTION_SET).childType(SINGLE_CHOICE).shape(FOUR_OPTIONS).points("1").tag(tag);
    }

    // ---- spec 4.3: Tieng Anh ---------------------------------------------------------------

    /** Q-39: English is one point per question; the spec 4.3 table has no points column, the template sets it. */
    @Test
    void spec43ListeningLine1EightSingleQuestionsWithAudio() {
        var filter = filter("ANH", Block.of(SINGLE_CHOICE).points("1").audio(AudioRequirement.REQUIRED));
        assertUnits("4.3 listening line 1", filter, 8);
        assertThat(candidates(filter)).allMatch(c -> c.code().startsWith("SEED-HSCA-ANH-LISTEN-SINGLE-"));
    }

    @Test
    void spec43ListeningLine2ThreeSetsOfFourWithAudio() {
        var sets = assertSets("4.3 listening line 2", filter("ANH", listeningSet()), 3, 4);
        assertThat(sets).allMatch(c -> c.code().startsWith("SEED-HSCA-ANH-LISTEN-SET4-"));
    }

    @Test
    void spec43ListeningLine3ThreeSetsOfFiveWithAudio() {
        var sets = assertSets("4.3 listening line 3", filter("ANH", listeningSet()), 3, 5);
        assertThat(sets).allMatch(c -> c.code().startsWith("SEED-HSCA-ANH-LISTEN-SET5-"));
    }

    @Test
    void spec43ReadingLine1FourSetsOfTenWithoutAudioFourOptions() {
        var sets = assertSets("4.3 reading line 1", filter("ANH", readingSet()), 4, 10);
        assertThat(sets).allMatch(c -> c.code().startsWith("SEED-HSCA-ANH-READING-SET-"));
    }

    /** The audio condition is the line's only discriminator between the two parts of the paper. */
    @Test
    void spec43ListeningAndReadingNeverOfferEachOthersSets() {
        var listening = candidates(filter("ANH", listeningSet()));
        var reading = candidates(filter("ANH", readingSet()));
        assertThat(listening).isNotEmpty().allMatch(c -> c.code().contains("-LISTEN-"));
        assertThat(reading).isNotEmpty().noneMatch(c -> c.code().contains("-LISTEN-"));
    }

    @Test
    void spec43AudioRequiredAndForbiddenPartitionEnglishSingles() {
        long any = source.count(filter("ANH", Block.of(SINGLE_CHOICE))).total();
        long required = source.count(filter("ANH", Block.of(SINGLE_CHOICE).audio(AudioRequirement.REQUIRED))).total();
        long forbidden = source.count(filter("ANH", Block.of(SINGLE_CHOICE).audio(AudioRequirement.FORBIDDEN))).total();
        assertThat(required).isGreaterThanOrEqualTo(8);
        assertThat(forbidden).isPositive();
        assertThat(required + forbidden).isEqualTo(any);
    }

    private static Block listeningSet() {
        return Block.of(QUESTION_SET).childType(SINGLE_CHOICE).points("1").audio(AudioRequirement.REQUIRED);
    }

    private static Block readingSet() {
        return Block.of(QUESTION_SET).childType(SINGLE_CHOICE).shape(FOUR_OPTIONS).points("1")
                .audio(AudioRequirement.FORBIDDEN);
    }

    /** True when some subset of the whole sets (each used once) holds exactly {@code target} questions. */
    private static boolean composable(List<Integer> sizes, int target) {
        boolean[] reachable = new boolean[target + 1];
        reachable[0] = true;
        for (int size : sizes) {
            for (int total = target; total >= size; total--) reachable[total] |= reachable[total - size];
        }
        return reachable[target];
    }
}
