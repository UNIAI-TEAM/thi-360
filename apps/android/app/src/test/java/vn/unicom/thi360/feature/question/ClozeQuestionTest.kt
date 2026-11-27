package vn.unicom.thi360.feature.question

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ClozeQuestionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clozeRendersTwoInlineSlotsFormulaCounterAndPreservesClearedEmptyStringKey() {
        val sample = QuestionSamplesLoader.loadByKey("cloze")
        var current by mutableStateOf<AnswerValue?>(null)
        val emitted = mutableListOf<AnswerValue>()
        var rootView: View? = null

        composeRule.setContent {
            rootView = LocalView.current
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

        composeRule.onNodeWithTag(QuestionTestTags.FRAME_TITLE).assertTextEquals("Câu 22–23")
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_TYPE_CHIP).assertTextEquals("Điền khuyết · 2")
        composeRule.onNodeWithTag(QuestionTestTags.CLOZE_COUNTER).assertTextEquals("Đã điền 0/2 ô")
        composeRule.onNodeWithTag(QuestionTestTags.clozeBlank("b1")).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.clozeBlank("b2")).assertIsDisplayed()

        val inputs = findAllEditTexts(rootView!!)
        assertEquals(2, inputs.size)
        assertEquals("Ô 1", inputs[0].hint.toString())
        assertEquals("Ô 2", inputs[1].hint.toString())
        assertEquals("Câu 22, ô trống 1", inputs[0].contentDescription.toString())
        assertEquals("Câu 23, ô trống 2", inputs[1].contentDescription.toString())

        composeRule.runOnIdle {
            inputs[0].setText("100")
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QuestionTestTags.CLOZE_COUNTER).assertTextEquals("Đã điền 1/2 ô")
        assertEquals(ClozeAnswer(linkedMapOf("b1" to "100")), emitted.last())

        composeRule.runOnIdle {
            inputs[0].setText("")
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QuestionTestTags.CLOZE_COUNTER).assertTextEquals("Đã điền 0/2 ô")
        val cleared = emitted.last() as ClozeAnswer
        assertTrue(cleared.blanks?.containsKey("b1") == true)
        assertEquals("", cleared.blanks?.get("b1"))
        assertEquals("""{"type":"CLOZE","blanks":{"b1":""}}""", AnswerValues.encodeToJson(cleared))
    }
}
