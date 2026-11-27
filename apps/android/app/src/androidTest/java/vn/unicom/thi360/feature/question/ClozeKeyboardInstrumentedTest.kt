package vn.unicom.thi360.feature.question

import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun lowerHalfClozeBlankScrollsAboveRealSoftKeyboardOn360dpWidth() {
        val sample = QuestionAndroidTestSupport.loadSamples().first { it.key == "cloze" }
        var answer by mutableStateOf<AnswerValue?>(null)
        val engine = RaTeXFormulaEngine(composeRule.activity)

        composeRule.runOnUiThread {
            composeRule.activity.window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
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

        composeRule.runOnUiThread {
            blankEditText.requestFocus()
            WindowInsetsControllerCompat(
                composeRule.activity.window,
                composeRule.activity.window.decorView,
            ).show(WindowInsetsCompat.Type.ime())
            val imm = composeRule.activity.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(blankEditText, InputMethodManager.SHOW_IMPLICIT)
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            val insets = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
            insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
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
}
