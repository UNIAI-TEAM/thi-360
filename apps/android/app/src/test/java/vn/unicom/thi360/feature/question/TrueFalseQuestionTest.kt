package vn.unicom.thi360.feature.question

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class TrueFalseQuestionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun togglesTrueFalseAndSecondTapRemovesStatementKey() {
        val sample = QuestionSamplesLoader.loadByKey("trueFalse")
        var current by mutableStateOf<AnswerValue?>(null)
        val emitted = mutableListOf<AnswerValue>()

        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = current,
                    mode = QuestionMode.Answering,
                    onAnswer = {
                        current = it
                        emitted.add(it)
                    },
                )
            }
        }

        val t1True = composeRule.onNodeWithTag(QuestionTestTags.tfTrue("t1"))
        val t1False = composeRule.onNodeWithTag(QuestionTestTags.tfFalse("t1"))
        t1True.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        t1False.assertIsDisplayed().assertHeightIsAtLeast(48.dp)

        t1True.performClick()
        composeRule.waitForIdle()
        assertEquals(TrueFalseAnswer(mapOf("t1" to true)), emitted.last())

        t1False.performClick()
        composeRule.waitForIdle()
        assertEquals(TrueFalseAnswer(mapOf("t1" to false)), emitted.last())

        t1False.performClick()
        composeRule.waitForIdle()
        val cleared = emitted.last() as TrueFalseAnswer
        assertFalse(cleared.statements?.containsKey("t1") == true)
        assertEquals(TrueFalseAnswer(emptyMap()), cleared)
    }
}
