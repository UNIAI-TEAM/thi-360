package vn.unicom.thi360.feature.question

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ShortAnswerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun numericShortAnswerDisplaysHintUsesNumericNoSuggestionsInputTypeAndPreservesSpaces() {
        val sample = QuestionSamplesLoader.loadByKey("short")
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

        composeRule.onNodeWithText("Số nguyên, không ghi đơn vị", substring = true).assertIsDisplayed()

        val editText = findFirstEditText(rootView!!)
        assertNotNull(editText)
        val inputType = editText!!.inputType
        assertEquals(InputType.TYPE_CLASS_NUMBER, inputType and InputType.TYPE_MASK_CLASS)
        assertTrue((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0)
        assertEquals(0, inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)

        composeRule.runOnIdle {
            editText.setText("  56 ")
        }
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue(text = "  56 "), emitted.last())
    }

    @Test
    fun textShortAnswerDisablesAutoCorrectAndPreservesQuotesAndHyphensVerbatim() {
        val item = QuestionItem.ShortAnswer(
            id = "sa-chem",
            displayNo = 8,
            stem = "<p>Nhập công thức</p>",
            answerFormat = AnswerFormat.CHEM_FORMULA,
            inputHint = "Viết công thức hóa học",
        )
        var current by mutableStateOf<AnswerValue?>(null)
        val emitted = mutableListOf<AnswerValue>()
        var rootView: View? = null

        composeRule.setContent {
            rootView = LocalView.current
            QuestionTestHost {
                QuestionView(
                    item = item,
                    answer = current,
                    mode = QuestionMode.Answering,
                    onAnswer = {
                        current = it
                        emitted.add(it)
                    },
                )
            }
        }

        val editText = findFirstEditText(rootView!!)!!
        assertEquals(InputType.TYPE_CLASS_TEXT, editText.inputType and InputType.TYPE_MASK_CLASS)
        assertTrue((editText.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0)
        assertEquals(0, editText.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)

        val verbatim = "\"CO2\" -- x"
        composeRule.runOnIdle {
            editText.setText(verbatim)
        }
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue(text = verbatim), emitted.last())
    }

    @Test
    fun shortAnswerFinalizesComposingOnFinishAndFocusLossWithoutErasing() {
        val item = QuestionItem.ShortAnswer(
            id = "sa-ime",
            displayNo = 9,
            stem = "<p>Nhập tên nguyên tố</p>",
            answerFormat = AnswerFormat.TEXT,
            inputHint = null,
        )
        var current by mutableStateOf<AnswerValue?>(null)
        val emitted = mutableListOf<ShortAnswerValue>()
        var rootView: View? = null

        composeRule.setContent {
            rootView = LocalView.current
            QuestionTestHost {
                QuestionView(
                    item = item,
                    answer = current,
                    mode = QuestionMode.Answering,
                    onAnswer = {
                        current = it
                        emitted.add(it as ShortAnswerValue)
                    },
                )
            }
        }

        val editText = findFirstEditText(rootView!!)!!
        composeRule.runOnIdle {
            editText.requestFocus()
            val ic = editText.onCreateInputConnection(EditorInfo())!!
            ic.setComposingText("Việt", 1)
        }
        composeRule.waitForIdle()
        assertTrue(emitted.none { it.text.orEmpty().contains("Việt") })

        composeRule.runOnIdle {
            val ic = editText.onCreateInputConnection(EditorInfo())!!
            ic.finishComposingText()
        }
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue("Việt"), emitted.last())
        assertEquals("Việt", editText.text.toString())

        composeRule.runOnIdle {
            val ic = editText.onCreateInputConnection(EditorInfo())!!
            ic.setComposingText(" Nam", 1)
        }
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue("Việt"), emitted.last())

        composeRule.runOnIdle {
            editText.clearFocus()
        }
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue("Việt Nam"), emitted.last())
        assertEquals("Việt Nam", editText.text.toString())
    }
}

internal fun findFirstEditText(view: View): EditText? {
    if (view is EditText) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val found = findFirstEditText(view.getChildAt(i))
            if (found != null) return found
        }
    }
    return null
}

internal fun findAllEditTexts(view: View): List<EditText> {
    val result = ArrayList<EditText>()
    fun walk(node: View) {
        if (node is EditText) {
            result.add(node)
        } else if (node is ViewGroup) {
            for (i in 0 until node.childCount) {
                walk(node.getChildAt(i))
            }
        }
    }
    walk(view)
    return result
}
