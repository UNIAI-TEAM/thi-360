package vn.unicom.thi360.feature.question

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.feature.question.model.AnswerFormat
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class NumericKeyRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun numericKeyRowHas48dpButtonsAccessibleLabelsInsertsAtCursorAndReplacesSelection() {
        val item = QuestionItem.ShortAnswer(
            id = "sa-fraction",
            displayNo = 12,
            stem = "<p>Nhập phân số</p>",
            answerFormat = AnswerFormat.FRACTION,
            inputHint = "Phân số tối giản",
        )
        var current by mutableStateOf<AnswerValue?>(ShortAnswerValue(""))
        var rootView: View? = null

        composeRule.setContent {
            rootView = LocalView.current
            QuestionTestHost {
                QuestionView(
                    item = item,
                    answer = current,
                    mode = QuestionMode.Answering,
                    onAnswer = { current = it },
                )
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.NUMERIC_KEY_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.SYMBOL_BAR).assertDoesNotExist()

        val expectedLabels = mapOf(
            "minus" to "Dấu trừ (-)",
            "slash" to "Dấu gạch chéo",
            "comma" to "Dấu phẩy",
            "dot" to "Dấu chấm",
        )
        for ((keyId, label) in expectedLabels) {
            composeRule.onNodeWithTag(QuestionTestTags.numericKey(keyId))
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assertContentDescriptionEquals(label)
        }

        val editText = findFirstEditText(rootView!!)!!
        composeRule.runOnIdle {
            editText.requestFocus()
        }
        composeRule.onNodeWithTag(QuestionTestTags.numericKey("minus")).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            editText.editableText.insert(editText.selectionStart, "3")
        }
        composeRule.onNodeWithTag(QuestionTestTags.numericKey("slash")).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            editText.editableText.insert(editText.selectionStart, "4")
        }
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue("-3/4"), current)
        assertTrue(editText.hasFocus())

        composeRule.runOnIdle {
            editText.setText("1234")
            editText.setSelection(1, 3)
        }
        composeRule.onNodeWithTag(QuestionTestTags.numericKey("comma")).performClick()
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue("1,4"), current)
        assertEquals(2, editText.selectionStart)
        assertTrue(editText.hasFocus())
    }
}
