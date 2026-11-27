package vn.unicom.thi360.feature.question

import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
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
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine

@RunWith(AndroidJUnit4::class)
class ImeCompositionInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun telexVietnameseCompositionWordCountSuppressionAndUndoRedoWorkAcrossEssayAndCloze() {
        val essayItem = QuestionItem.Essay(
            id = "ime-essay",
            displayNo = 1,
            stem = "<p>Viết bài bằng Telex</p>",
            wordLimit = null,
        )
        var essayAnswer by mutableStateOf<AnswerValue?>(null)
        val essayEmitted = mutableListOf<EssayAnswer>()

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Thi360Theme.colors.bgPage)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    QuestionView(
                        item = essayItem,
                        answer = essayAnswer,
                        mode = QuestionMode.Answering,
                        onAnswer = {
                            essayAnswer = it
                            essayEmitted.add(it as EssayAnswer)
                        },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val editor = QuestionAndroidTestSupport.findAllEditTexts(
            composeRule.activity.window.decorView,
        ).first()

        composeRule.runOnUiThread {
            editor.requestFocus()
            val ic = editor.onCreateInputConnection(EditorInfo())
            assertNotNull(ic)
            ic!!.setComposingText("Tie", 1)
            ic.setComposingText("Tiê", 1)
            ic.setComposingText("Tiếng", 1)
            ic.commitText("Tiếng ", 1)
            ic.setComposingText("Vie", 1)
            ic.setComposingText("Viê", 1)
            ic.setComposingText("Việt", 1)
            ic.commitText("Việt ", 1)
            ic.setComposingText("cos", 1)
            ic.commitText("có ", 1)
            ic.setComposingText("daaus", 1)
            ic.commitText("dấu", 1)
        }
        composeRule.waitForIdle()

        assertEquals("Tiếng Việt có dấu", editor.text.toString())
        assertEquals(EssayAnswer("<p>Tiếng Việt có dấu</p>", 4), essayEmitted.last())
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_WORD_COUNT).assertTextEquals("4 chữ")

        essayEmitted.clear()
        composeRule.runOnUiThread {
            editor.setText("")
        }
        composeRule.waitForIdle()
        essayEmitted.clear()

        composeRule.runOnUiThread {
            val ic = editor.onCreateInputConnection(EditorInfo())!!
            ic.commitText("Bài làm tiếng ", 1)
            ic.setComposingText("Vieej", 1)
        }
        composeRule.waitForIdle()

        assertTrue(essayEmitted.isNotEmpty())
        assertTrue(
            "Uncommitted composing text 'Vieej' must never leak to onAnswer",
            essayEmitted.none { it.html.orEmpty().contains("Vieej") },
        )
        assertEquals("<p>Bài làm tiếng </p>", essayEmitted.last().html)

        composeRule.runOnUiThread {
            val ic = editor.onCreateInputConnection(EditorInfo())!!
            ic.commitText("Việt", 1)
        }
        composeRule.waitForIdle()
        assertEquals(EssayAnswer("<p>Bài làm tiếng Việt</p>", 4), essayEmitted.last())

        composeRule.runOnUiThread {
            editor.setText("")
        }
        composeRule.waitForIdle()
        essayEmitted.clear()

        composeRule.runOnUiThread {
            val ic = editor.onCreateInputConnection(EditorInfo())!!
            ic.setComposingText("Dduowngf", 1)
            ic.commitText("Đường thẳng", 1)
        }
        composeRule.waitForIdle()
        assertEquals("Đường thẳng", editor.text.toString())

        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_UNDO).performClick()
        composeRule.waitForIdle()
        assertEquals("", editor.text.toString())

        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_REDO).performClick()
        composeRule.waitForIdle()
        assertEquals("Đường thẳng", editor.text.toString())
        assertEquals(EssayAnswer("<p>Đường thẳng</p>", 2), essayEmitted.last())
    }

    @Test
    fun clozeSuppressesUncommittedTelexComposingTextUntilCommitted() {
        val sample = QuestionAndroidTestSupport.loadSamples().first { it.key == "clozeText" }
        val engine = RaTeXFormulaEngine(composeRule.activity)
        var clozeAnswer by mutableStateOf<AnswerValue?>(null)
        val emitted = mutableListOf<ClozeAnswer>()

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Thi360Theme.colors.bgPage)
                            .padding(16.dp),
                    ) {
                        QuestionView(
                            item = sample.item,
                            answer = clozeAnswer,
                            mode = QuestionMode.Answering,
                            onAnswer = {
                                clozeAnswer = it
                                emitted.add(it as ClozeAnswer)
                            },
                            formulaEngine = engine,
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val blank1 = QuestionAndroidTestSupport.findAllEditTexts(
            composeRule.activity.window.decorView,
        ).first()

        composeRule.runOnUiThread {
            blank1.requestFocus()
            val ic = blank1.onCreateInputConnection(EditorInfo())!!
            ic.setComposingText("Vieej", 1)
        }
        composeRule.waitForIdle()

        assertFalse(
            "Cloze must not emit uncommitted composing text 'Vieej'",
            emitted.any { it.blanks?.get("b1")?.contains("Vieej") == true },
        )

        composeRule.runOnUiThread {
            val ic = blank1.onCreateInputConnection(EditorInfo())!!
            ic.commitText("Việt", 1)
        }
        composeRule.waitForIdle()
        assertEquals("Việt", emitted.last().blanks?.get("b1"))
    }

    @Test
    fun finishComposingTextAndFocusLossFinalizeVietnameseWithoutErasingAcrossShortClozeAndEssay() {
        val shortItem = QuestionItem.ShortAnswer(
            id = "ime-r2-short",
            displayNo = 1,
            stem = "<p>Trả lời ngắn Telex</p>",
            answerFormat = AnswerFormat.TEXT,
            inputHint = null,
        )
        val clozeSample = QuestionAndroidTestSupport.loadSamples().first { it.key == "clozeText" }
        val essayItem = QuestionItem.Essay(
            id = "ime-r2-essay",
            displayNo = 3,
            stem = "<p>Tự luận Telex</p>",
            wordLimit = null,
        )
        val engine = RaTeXFormulaEngine(composeRule.activity)
        var shortAns by mutableStateOf<AnswerValue?>(null)
        var clozeAns by mutableStateOf<AnswerValue?>(null)
        var essayAns by mutableStateOf<AnswerValue?>(null)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Thi360Theme.colors.bgPage)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        QuestionView(
                            item = shortItem,
                            answer = shortAns,
                            mode = QuestionMode.Answering,
                            onAnswer = { shortAns = it },
                        )
                        QuestionView(
                            item = clozeSample.item,
                            answer = clozeAns,
                            mode = QuestionMode.Answering,
                            onAnswer = { clozeAns = it },
                            formulaEngine = engine,
                        )
                        QuestionView(
                            item = essayItem,
                            answer = essayAns,
                            mode = QuestionMode.Answering,
                            onAnswer = { essayAns = it },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val edits = QuestionAndroidTestSupport.findAllEditTexts(composeRule.activity.window.decorView)
        assertTrue("Expected ShortAnswer + Cloze blanks + Essay EditTexts, got ${edits.size}", edits.size >= 3)
        val shortEdit = edits.first()
        val clozeEdit = edits[1]
        val essayEdit = edits.last()

        composeRule.runOnUiThread {
            shortEdit.requestFocus()
            val ic = shortEdit.onCreateInputConnection(EditorInfo())!!
            ic.setComposingText("Việt", 1)
        }
        composeRule.waitForIdle()
        assertFalse((shortAns as? ShortAnswerValue)?.text.orEmpty().contains("Việt"))

        composeRule.runOnUiThread {
            val ic = shortEdit.onCreateInputConnection(EditorInfo())!!
            ic.finishComposingText()
        }
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue("Việt"), shortAns)
        assertEquals("Việt", shortEdit.text.toString())

        composeRule.runOnUiThread {
            val ic = shortEdit.onCreateInputConnection(EditorInfo())!!
            ic.setComposingText(" Nam", 1)
        }
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue("Việt"), shortAns)

        composeRule.runOnUiThread {
            clozeEdit.requestFocus()
        }
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue("Việt Nam"), shortAns)
        assertEquals("Việt Nam", shortEdit.text.toString())

        composeRule.runOnUiThread {
            val ic = clozeEdit.onCreateInputConnection(EditorInfo())!!
            ic.setComposingText("Nước", 1)
        }
        composeRule.waitForIdle()
        assertFalse((clozeAns as? ClozeAnswer)?.blanks?.get("b1").orEmpty().contains("Nước"))

        composeRule.runOnUiThread {
            val ic = clozeEdit.onCreateInputConnection(EditorInfo())!!
            ic.finishComposingText()
        }
        composeRule.waitForIdle()
        assertEquals("Nước", (clozeAns as? ClozeAnswer)?.blanks?.get("b1"))
        assertEquals("Nước", clozeEdit.text.toString())

        composeRule.runOnUiThread {
            val ic = clozeEdit.onCreateInputConnection(EditorInfo())!!
            ic.setComposingText(" cất", 1)
        }
        composeRule.waitForIdle()
        assertEquals("Nước", (clozeAns as? ClozeAnswer)?.blanks?.get("b1"))

        composeRule.runOnUiThread {
            essayEdit.requestFocus()
        }
        composeRule.waitForIdle()
        assertEquals("Nước cất", (clozeAns as? ClozeAnswer)?.blanks?.get("b1"))
        assertEquals("Nước cất", clozeEdit.text.toString())

        composeRule.runOnUiThread {
            val ic = essayEdit.onCreateInputConnection(EditorInfo())!!
            ic.setComposingText("Tiếng", 1)
        }
        composeRule.waitForIdle()
        assertFalse((essayAns as? EssayAnswer)?.html.orEmpty().contains("Tiếng"))

        composeRule.runOnUiThread {
            val ic = essayEdit.onCreateInputConnection(EditorInfo())!!
            ic.finishComposingText()
        }
        composeRule.waitForIdle()
        assertEquals(EssayAnswer("<p>Tiếng</p>", 1), essayAns)
        assertEquals("Tiếng", essayEdit.text.toString())

        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_UNDO).performClick()
        composeRule.waitForIdle()
        assertEquals("", essayEdit.text.toString())
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_REDO).performClick()
        composeRule.waitForIdle()
        assertEquals("Tiếng", essayEdit.text.toString())

        composeRule.runOnUiThread {
            val ic = essayEdit.onCreateInputConnection(EditorInfo())!!
            ic.commitText(" ", 1)
            ic.setComposingText("Việt", 1)
        }
        composeRule.waitForIdle()
        assertEquals("<p>Tiếng </p>", (essayAns as? EssayAnswer)?.html)

        composeRule.runOnUiThread {
            shortEdit.requestFocus()
        }
        composeRule.waitForIdle()
        assertEquals(EssayAnswer("<p>Tiếng Việt</p>", 2), essayAns)
        assertEquals("Tiếng Việt", essayEdit.text.toString())
    }
}
