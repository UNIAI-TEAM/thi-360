package vn.unicom.thi360.feature.question

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionView
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine

@RunWith(AndroidJUnit4::class)
class ClozeKeyboardInstrumentedTest {

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
    fun lowerHalfClozeBlankScrollsAboveRealSoftKeyboardOn360dpWidth() {
        val sample = QuestionAndroidTestSupport.loadSamples().first { it.key == "cloze" }
        var answer by mutableStateOf<AnswerValue?>(null)
        val engine = RaTeXFormulaEngine(composeRule.activity)

        composeRule.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(composeRule.activity.window, false)
        }

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .width(360.dp)
                            .background(Thi360Theme.colors.bgPage)
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Spacer(modifier = Modifier.height(480.dp))
                        QuestionView(
                            item = sample.item,
                            answer = answer,
                            mode = QuestionMode.Answering,
                            onAnswer = { answer = it },
                            formulaEngine = engine,
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val blankEditText = QuestionAndroidTestSupport.findAllEditTexts(
            composeRule.activity.window.decorView,
        ).first()

        awaitAndShowSoftKeyboard(blankEditText)

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            val decor = composeRule.activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)
            val imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            val loc = IntArray(2)
            blankEditText.getLocationOnScreen(loc)
            val fieldBottom = loc[1] + blankEditText.height
            imeBottom > 0 && fieldBottom <= decor.height - imeBottom + 8
        }

        val decor = composeRule.activity.window.decorView
        val imeBottom = ViewCompat.getRootWindowInsets(decor)
            ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        val loc = IntArray(2)
        blankEditText.getLocationOnScreen(loc)
        val fieldBottom = loc[1] + blankEditText.height
        assertTrue("Expected IME height > 0, got $imeBottom", imeBottom > 0)
        assertTrue(
            "Expected cloze blank bottom ($fieldBottom) above keyboard top (${decor.height - imeBottom})",
            fieldBottom <= decor.height - imeBottom + 8,
        )

        composeRule.runOnUiThread {
            blankEditText.setText("100")
        }
        composeRule.waitForIdle()
        assertEquals(ClozeAnswer(linkedMapOf("b1" to "100")), answer)
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
