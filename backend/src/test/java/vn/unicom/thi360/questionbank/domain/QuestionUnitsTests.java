package vn.unicom.thi360.questionbank.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import vn.unicom.thi360.questionbank.domain.QuestionUnits.QuestionUnit;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Đơn vị đếm vào ma trận (spec 4.3, D22): Bộ câu hỏi = số câu con (dạng của từng câu con),
 * Điền khuyết = số vị trí (dạng CLOZE), mọi dạng khác = 1 kể cả Đúng/Sai và Ghép đôi.
 */
class QuestionUnitsTests {

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
        List<QuestionUnit> units = QuestionUnits.units(QuestionBodyValidationTests.multipleChoiceBody());
        assertEquals(1, units.size());
        assertEquals(QuestionType.MULTIPLE_CHOICE, units.get(0).answerType());
    }

    @Test
    void questionSetUnitsAreChildren() {
        List<QuestionUnit> units = QuestionUnits.units(QuestionBodyValidationTests.questionSetBody());
        assertEquals(2, units.size());
        assertEquals(QuestionType.SINGLE_CHOICE, units.get(0).answerType());
        assertEquals(QuestionType.SHORT_ANSWER, units.get(1).answerType());
        assertEquals("children[0]", units.get(0).reference());
    }

    @Test
    void clozeUnitsAreBlanks() {
        List<QuestionUnit> units = QuestionUnits.units(QuestionBodyValidationTests.clozeBody());
        assertEquals(2, units.size());
        assertEquals(QuestionType.CLOZE, units.get(0).answerType());
        assertEquals("b1", units.get(0).reference());
        assertEquals("b2", units.get(1).reference());
    }

    @Test
    void nullBodyHasNoUnits() {
        assertEquals(0, QuestionUnits.count(null));
    }
}
