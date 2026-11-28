package vn.unicom.thi360.feature.question

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

    private var hasSavedShowImeSetting = false
    private var previousShowImeWithHardKeyboard: String? = null

    @Before
    fun enableSoftImeWithHardKeyboard() {
        val current = runShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        previousShowImeWithHardKeyboard = current.takeIf { it.isNotEmpty() && it != "null" }
        hasSavedShowImeSetting = true
        runShellCommand("settings put secure show_ime_with_hard_keyboard 1")
    }

    @After
    fun restoreSoftImeWithHardKeyboard() {
        if (!hasSavedShowImeSetting) return
        val previous = previousShowImeWithHardKeyboard
        if (previous == null) {
            runShellCommand("settings delete secure show_ime_with_hard_keyboard")
        } else {
            runShellCommand("settings put secure show_ime_with_hard_keyboard $previous")
        }
        hasSavedShowImeSetting = false
    }

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
            WindowCompat.setDecorFitsSystemWindows(composeRule.activity.window, false)
            composeRule.activity.window.setSoftInputMode(
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

        awaitAndShowSoftKeyboard(editText)

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

    private fun awaitAndShowSoftKeyboard(editText: EditText) {
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            var hasWindowFocus = false
            composeRule.runOnUiThread {
                hasWindowFocus = composeRule.activity.hasWindowFocus()
            }
            hasWindowFocus
        }

        val imm = requireNotNull(composeRule.activity.getSystemService(InputMethodManager::class.java))
        composeRule.runOnUiThread {
            editText.requestFocus()
        }

        var lastFocusRequestAt = SystemClock.uptimeMillis()
        var lastRebindAttemptAt = lastFocusRequestAt
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            var served = false
            composeRule.runOnUiThread {
                val hasWindowFocus = composeRule.activity.hasWindowFocus()
                val now = SystemClock.uptimeMillis()
                if (hasWindowFocus && !editText.hasFocus() && now - lastFocusRequestAt >= 300L) {
                    editText.requestFocus()
                    lastFocusRequestAt = now
                } else if (
                    hasWindowFocus &&
                    editText.hasFocus() &&
                    !imm.isActive(editText) &&
                    now - lastRebindAttemptAt >= 1_000L
                ) {
                    editText.clearFocus()
                    editText.requestFocus()
                    lastRebindAttemptAt = now
                }
                served = hasWindowFocus && editText.hasFocus() && imm.isActive(editText)
            }
            served
        }

        var lastShowAttemptAt = Long.MIN_VALUE
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            var imeVisible = false
            composeRule.runOnUiThread {
                val window = composeRule.activity.window
                val decorView = window.decorView
                val insets = ViewCompat.getRootWindowInsets(decorView)
                if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                    imeVisible = true
                    return@runOnUiThread
                }
                val now = SystemClock.uptimeMillis()
                if (lastShowAttemptAt == Long.MIN_VALUE || now - lastShowAttemptAt >= 300L) {
                    if (!editText.hasFocus()) {
                        editText.requestFocus()
                    }
                    if (composeRule.activity.hasWindowFocus() && imm.isActive(editText)) {
                        WindowInsetsControllerCompat(window, decorView).show(WindowInsetsCompat.Type.ime())
                        imm.showSoftInput(editText, 0)
                    }
                    lastShowAttemptAt = now
                }
            }
            imeVisible
        }
    }

    private fun runShellCommand(command: String): String {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        return uiAutomation.executeShellCommand(command).use { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        }
    }
}
