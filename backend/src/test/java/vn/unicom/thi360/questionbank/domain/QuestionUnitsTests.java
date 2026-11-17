package vn.unicom.thi360.questionbank.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.unicom.thi360.questionbank.domain.QuestionUnits.QuestionUnit;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Đơn vị đếm vào ma trận (spec 4.3, D22): Bộ câu hỏi = số câu con (dạng của từng câu con),
 * Điền khuyết = số vị trí (dạng CLOZE), mọi dạng khác = 1 kể cả Đúng/Sai và Ghép đôi.
 * Đơn vị tham chiếu bản ghi câu bằng (questionId, versionNo) — câu con là dòng riêng.
 */
class QuestionUnitsTests {

    private static final UUID QID = UUID.fromString("00000000-0000-7000-8000-0000000000a1");
    private static final UUID CHILD1 = UUID.fromString("00000000-0000-7000-8000-0000000000b1");
    private static final UUID CHILD2 = UUID.fromString("00000000-0000-7000-8000-0000000000b2");

    @Test
    void standaloneCountsOne() {
        assertEquals(1, QuestionUnits.count(QuestionBodyValidationTests.singleChoiceBody()));
        assertEquals(1, QuestionUnits.count(QuestionBodyValidationTests.shortAnswerBody()));
        assertEquals(1, QuestionUnits.count(QuestionBodyValidationTests.essayBody()));
    }

    @Test
    void trueFalseAndMatchingCountOne() {
        // Đúng/Sai nhiều mệnh đề và Ghép đôi nhiều cặp vẫn là một đơn vị (spec 4.3).
        assertEquals(1, QuestionUnits.count(QuestionBodyValidationTests.trueFalseBody()));
        assertEquals(1, QuestionUnits.count(QuestionBodyValidationTests.matchingBody()));
    }

    @Test
    void unitTypeIsBodyType() {
        List<QuestionUnit> units = QuestionUnits.units(QID, 3, QuestionBodyValidationTests.multipleChoiceBody());
        assertEquals(1, units.size());
        assertEquals(QuestionType.MULTIPLE_CHOICE, units.get(0).answerType());
        assertEquals(QID, units.get(0).questionId());
        assertEquals(3, units.get(0).versionNo());
        assertNull(units.get(0).reference());
    }

    @Test
    void questionSetUnitsAreChildren() {
        QuestionBody set = new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET,
            "<p>p</p>",
            List.of(
                new QuestionBody.ChildQuestion(QuestionType.SINGLE_CHOICE, "<p>c1</p>",
                    QuestionBodyValidationTests.singleChoiceBody(), null, null, null, null, List.of(), CHILD1),
                new QuestionBody.ChildQuestion(QuestionType.SHORT_ANSWER, "<p>c2</p>",
                    QuestionBodyValidationTests.shortAnswerBody(), null, null, null, null, List.of(), CHILD2)));
        List<QuestionUnit> units = QuestionUnits.units(QID, 7, set);
        assertEquals(2, units.size());
        // Câu con tham chiếu bằng id bản ghi của nó, version_no là của bộ (spec 4.1).
        assertEquals(CHILD1, units.get(0).questionId());
        assertEquals(CHILD2, units.get(1).questionId());
        assertEquals(7, units.get(0).versionNo());
        assertEquals(QuestionType.SINGLE_CHOICE, units.get(0).answerType());
        assertEquals(QuestionType.SHORT_ANSWER, units.get(1).answerType());
        assertNull(units.get(0).reference());
    }

    @Test
    void childWithoutIdHasNullQuestionId() {
        // Câu con mới trong request (chưa lưu, chưa có id) vẫn đếm được.
        QuestionBody set = new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET,
            "<p>p</p>",
            List.of(new QuestionBody.ChildQuestion(QuestionType.ESSAY, "<p>c</p>",
                QuestionBodyValidationTests.essayBody(), null, null, null, null, List.of(), null)));
        List<QuestionUnit> units = QuestionUnits.units(QID, null, set);
        assertEquals(1, units.size());
        assertNull(units.get(0).questionId());
        assertNull(units.get(0).versionNo());
    }

    @Test
    void clozeUnitsAreBlanks() {
        List<QuestionUnit> units = QuestionUnits.units(QID, 2, QuestionBodyValidationTests.clozeBody());
        assertEquals(2, units.size());
        assertEquals(QuestionType.CLOZE, units.get(0).answerType());
        assertEquals(QID, units.get(0).questionId());
        assertEquals(2, units.get(0).versionNo());
        assertEquals("b1", units.get(0).reference());
        assertEquals("b2", units.get(1).reference());
    }

    @Test
    void nullBodyHasNoUnits() {
        assertEquals(0, QuestionUnits.count(null));
    }
}
