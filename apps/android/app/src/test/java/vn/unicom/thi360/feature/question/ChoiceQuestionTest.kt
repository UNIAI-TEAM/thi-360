package vn.unicom.thi360.feature.question

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ChoiceQuestionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun letterMapsToServerId() {
        val sample = QuestionSamplesLoader.loadByKey("single")
        var currentAnswer by mutableStateOf<AnswerValue?>(null)
        val emitted = mutableListOf<AnswerValue>()

        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = currentAnswer,
                    mode = QuestionMode.Answering,
                    onAnswer = {
                        currentAnswer = it
                        emitted.add(it)
                    },
                )
            }
        }

        val s3Bounds = composeRule.onNodeWithTag(QuestionTestTags.choiceOption("s3"))
            .assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        val s1Bounds = composeRule.onNodeWithTag(QuestionTestTags.choiceOption("s1"))
            .fetchSemanticsNode().boundsInRoot
        assert(s3Bounds.top < s1Bounds.top)

        composeRule.onNodeWithText("B.").performClick()
        composeRule.waitForIdle()
        assertEquals(SingleChoiceAnswer(optionId = "s1"), emitted.last())
    }

    @Test
    fun singleChoiceTapSelectsAndSecondTapClearsToSingleChoiceNullJson() {
        val sample = QuestionSamplesLoader.loadByKey("single")
        var currentAnswer by mutableStateOf<AnswerValue?>(null)
        val emitted = mutableListOf<AnswerValue>()

        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = currentAnswer,
                    mode = QuestionMode.Answering,
                    onAnswer = {
                        currentAnswer = it
                        emitted.add(it)
                    },
                )
            }
        }

        composeRule.onNodeWithText("A.").performClick()
        composeRule.waitForIdle()
        assertEquals(SingleChoiceAnswer(optionId = "s3"), emitted.last())

        composeRule.onNodeWithText("A.").performClick()
        composeRule.waitForIdle()
        val cleared = emitted.last()
        assertEquals(SingleChoiceAnswer(optionId = null), cleared)
        assertEquals("""{"type":"SINGLE_CHOICE"}""", AnswerValues.encodeToJson(cleared))
    }

    @Test
    fun multipleChoiceSelectsInClickOrderAndClearsToEmptyArray() {
        val sample = QuestionSamplesLoader.loadByKey("multiple")
        var currentAnswer by mutableStateOf<AnswerValue?>(null)
        val emitted = mutableListOf<AnswerValue>()

        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = currentAnswer,
                    mode = QuestionMode.Answering,
                    onAnswer = {
                        currentAnswer = it
                        emitted.add(it)
                    },
                )
            }
        }

        composeRule.onNodeWithText("C.").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("A.").performClick()
        composeRule.waitForIdle()
        assertEquals(MultipleChoiceAnswer(optionIds = listOf("m3", "m1")), emitted.last())

        composeRule.onNodeWithText("C.").performClick()
        composeRule.waitForIdle()
        assertEquals(MultipleChoiceAnswer(optionIds = listOf("m1")), emitted.last())

        composeRule.onNodeWithText("A.").performClick()
        composeRule.waitForIdle()
        val empty = emitted.last()
        assertEquals(MultipleChoiceAnswer(optionIds = emptyList()), empty)
        assertEquals("""{"type":"MULTIPLE_CHOICE","optionIds":[]}""", AnswerValues.encodeToJson(empty))
    }
}
