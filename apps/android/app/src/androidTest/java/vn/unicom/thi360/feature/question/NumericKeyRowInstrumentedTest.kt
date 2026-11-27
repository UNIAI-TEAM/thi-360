package vn.unicom.thi360.feature.question

import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.model.AnswerFormat
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(AndroidJUnit4::class)
class NumericKeyRowInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun numericExtraKeysInsertMinusThreeOverFourWhileKeepingSoftKeyboardOpen() {
        val item = QuestionItem.ShortAnswer(
            id = "inst-fraction",
            displayNo = 12,
            stem = "<p>Nhập phân số âm</p>",
            answerFormat = AnswerFormat.FRACTION,
            inputHint = "Phân số tối giản",
        )
        var answer by mutableStateOf<AnswerValue?>(ShortAnswerValue(""))

        composeRule.runOnUiThread {
            composeRule.activity.window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
            )
        }

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Thi360Theme.colors.bgPage)
                        .padding(16.dp),
                ) {
                    QuestionView(
                        item = item,
                        answer = answer,
                        mode = QuestionMode.Answering,
                        onAnswer = { answer = it },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val editText = QuestionAndroidTestSupport.findAllEditTexts(
            composeRule.activity.window.decorView,
        ).first()

        composeRule.runOnUiThread {
            editText.requestFocus()
            WindowInsetsControllerCompat(
                composeRule.activity.window,
                composeRule.activity.window.decorView,
            ).show(WindowInsetsCompat.Type.ime())
            val imm = composeRule.activity.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            val insets = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
            insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }

        composeRule.onNodeWithTag(QuestionTestTags.numericKey("minus")).performClick()
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            editText.editableText.insert(editText.selectionStart, "3")
        }
        composeRule.onNodeWithTag(QuestionTestTags.numericKey("slash")).performClick()
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            editText.editableText.insert(editText.selectionStart, "4")
        }
        composeRule.waitForIdle()

        assertEquals(ShortAnswerValue("-3/4"), answer)
        assertTrue("EditText must retain focus after tapping numeric keys", editText.hasFocus())
        val imeVisibleAfter = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        assertTrue("Soft keyboard must remain open after tapping numeric extra keys", imeVisibleAfter)
    }
}
