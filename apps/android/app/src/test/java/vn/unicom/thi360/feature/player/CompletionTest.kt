package vn.unicom.thi360.feature.player

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.Cloze
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.ClozeBlankView
import vn.unicom.thi360.core.network.generated.model.Essay
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.Matching
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoice
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SideView
import vn.unicom.thi360.core.network.generated.model.SingleChoice
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.StatementView
import vn.unicom.thi360.core.network.generated.model.TrueFalse
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer

class CompletionTest {

    @Test
    fun classifiesAllSevenLeafQuestionTypesAccurately() {
        val sc = AttemptItemView(
            itemId = UUID.randomUUID(),
            questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
            questionBody = SingleChoice(options = emptyList()),
        )
        assertEquals(ItemCompletion.Blank, Completion.of(sc, null))
        assertEquals(ItemCompletion.Blank, Completion.of(sc, SingleChoiceAnswer(optionId = "  ")))
        assertEquals(ItemCompletion.Answered, Completion.of(sc, SingleChoiceAnswer(optionId = "opt-a")))

        val mc = AttemptItemView(
            itemId = UUID.randomUUID(),
            questionType = AttemptItemView.QuestionType.MULTIPLE_CHOICE,
            questionBody = MultipleChoice(options = emptyList()),
        )
        assertEquals(ItemCompletion.Blank, Completion.of(mc, MultipleChoiceAnswer(optionIds = emptyList())))
        assertEquals(ItemCompletion.Answered, Completion.of(mc, MultipleChoiceAnswer(optionIds = listOf("a"))))

        val sa = AttemptItemView(
            itemId = UUID.randomUUID(),
            questionType = AttemptItemView.QuestionType.SHORT_ANSWER,
            questionBody = ShortAnswer(),
        )
        assertEquals(ItemCompletion.Blank, Completion.of(sa, ShortAnswerValue(text = "   ")))
        assertEquals(ItemCompletion.Answered, Completion.of(sa, ShortAnswerValue(text = "42")))

        val cloze = AttemptItemView(
            itemId = UUID.randomUUID(),
            questionType = AttemptItemView.QuestionType.CLOZE,
            questionBody = Cloze(
                blanks = listOf(ClozeBlankView(id = "b1"), ClozeBlankView(id = "b2")),
            ),
        )
        assertEquals(ItemCompletion.Blank, Completion.of(cloze, ClozeAnswer(blanks = mapOf("b1" to "  "))))
        assertEquals(ItemCompletion.Partial, Completion.of(cloze, ClozeAnswer(blanks = mapOf("b1" to "ans"))))
        assertEquals(
            ItemCompletion.Answered,
            Completion.of(cloze, ClozeAnswer(blanks = mapOf("b1" to "a", "b2" to "b"))),
        )

        val tf = AttemptItemView(
            itemId = UUID.randomUUID(),
            questionType = AttemptItemView.QuestionType.TRUE_FALSE,
            questionBody = TrueFalse(
                statements = listOf(
                    StatementView(id = "s1", content = "S1"),
                    StatementView(id = "s2", content = "S2"),
                ),
            ),
        )
        assertEquals(ItemCompletion.Blank, Completion.of(tf, TrueFalseAnswer(statements = emptyMap())))
        assertEquals(ItemCompletion.Partial, Completion.of(tf, TrueFalseAnswer(statements = mapOf("s1" to true))))
        assertEquals(
            ItemCompletion.Answered,
            Completion.of(tf, TrueFalseAnswer(statements = mapOf("s1" to true, "s2" to false))),
        )

        val matching = AttemptItemView(
            itemId = UUID.randomUUID(),
            questionType = AttemptItemView.QuestionType.MATCHING,
            questionBody = Matching(
                left = listOf(
                    SideView(id = "l1", content = "L1"),
                    SideView(id = "l2", content = "L2"),
                ),
                right = listOf(
                    SideView(id = "r1", content = "R1"),
                    SideView(id = "r2", content = "R2"),
                ),
            ),
        )
        assertEquals(ItemCompletion.Blank, Completion.of(matching, MatchingAnswer(pairs = emptyMap())))
        assertEquals(ItemCompletion.Partial, Completion.of(matching, MatchingAnswer(pairs = mapOf("l1" to "r1"))))
        assertEquals(
            ItemCompletion.Answered,
            Completion.of(matching, MatchingAnswer(pairs = mapOf("l1" to "r1", "l2" to "r2"))),
        )

        val essay = AttemptItemView(
            itemId = UUID.randomUUID(),
            questionType = AttemptItemView.QuestionType.ESSAY,
            questionBody = Essay(wordLimit = 100),
        )
        assertEquals(ItemCompletion.Blank, Completion.of(essay, EssayAnswer(html = "<p>   </p>", wordCount = 0)))
        assertEquals(
            ItemCompletion.Answered,
            Completion.of(essay, EssayAnswer(html = "<p>Xin chào các bạn</p>", wordCount = 4)),
        )
    }

    @Test
    fun evaluatesEssayWordLimitStatus() {
        val essayWithLimit = AttemptItemView(
            itemId = UUID.randomUUID(),
            questionType = AttemptItemView.QuestionType.ESSAY,
            questionBody = Essay(wordLimit = 5),
        )
        val under = Completion.essayLimit(essayWithLimit, EssayAnswer(html = "<p>Một hai ba</p>", wordCount = 3))
        assertTrue(under.isEssay)
        assertEquals(3, under.wordCount)
        assertEquals(5, under.wordLimit)
        assertTrue(under.underLimit)

        val enough = Completion.essayLimit(
            essayWithLimit,
            EssayAnswer(html = "<p>Một hai ba bốn năm</p>", wordCount = 5),
        )
        assertFalse(enough.underLimit)
        assertEquals(5, enough.wordCount)
    }
}
