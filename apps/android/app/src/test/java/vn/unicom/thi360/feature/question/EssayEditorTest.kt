package vn.unicom.thi360.feature.question

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.feature.question.logic.EssayHtml
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.EssayEditText
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

class EssayHtmlTest {

    @Test
    fun sanitizePastedHtmlDropsImagesLinksColorsAndUnderlineWhileKeepingBoldItalicAndLists() {
        val dirty = """
            <p style="color:red"><u>Gạch chân</u> và <strong>đậm</strong> <a href="https://evil.example">liên kết</a> <img src="https://evil.example/x.png" /></p>
            <ul><li><p><span style="font-size:24px"><em>Mục một</em></span></p></li></ul>
        """.trimIndent()
        val clean = EssayHtml.sanitizePastedHtml(dirty)
        assertFalse(clean.contains("<img"))
        assertFalse(clean.contains("<a"))
        assertFalse(clean.contains("style="))
        assertFalse(clean.contains("<u>"))
        assertFalse(clean.contains("</u>"))
        assertTrue(clean.contains("<strong>đậm</strong>"))
        assertTrue(clean.contains("<ul><li><p><em>Mục một</em></p></li></ul>"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class EssayEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun essayEditorProducesExactHtmlAndWordCountClearsToEmptyAndSanitizesPaste() {
        val sample = QuestionSamplesLoader.loadByKey("essay")
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

        val buttons = listOf(
            QuestionTestTags.ESSAY_BTN_BOLD,
            QuestionTestTags.ESSAY_BTN_ITALIC,
            QuestionTestTags.ESSAY_BTN_BULLET,
            QuestionTestTags.ESSAY_BTN_ORDERED,
            QuestionTestTags.ESSAY_BTN_UNDO,
            QuestionTestTags.ESSAY_BTN_REDO,
        )
        for (tag in buttons) {
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_UNDO).assertIsNotEnabled()
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_REDO).assertIsNotEnabled()

        val editor = findFirstEditText(rootView!!) as EssayEditText
        composeRule.runOnIdle {
            editor.editableText.append("Mở bài\n")
            editor.setSelection(editor.text.length)
        }
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_ORDERED).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            editor.editableText.append("Ý một")
        }
        composeRule.waitForIdle()

        val expected = EssayAnswer(
            html = "<p>Mở bài</p><ol><li><p>Ý một</p></li></ol>",
            wordCount = 4,
        )
        assertEquals(expected, emitted.last())

        composeRule.runOnIdle {
            editor.editableText.clear()
        }
        composeRule.waitForIdle()
        assertEquals(EssayAnswer(html = "", wordCount = 0), emitted.last())

        composeRule.runOnIdle {
            val clipboard = editor.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newHtmlText(
                    "paste",
                    "Văn bản dán",
                    """<p style="color:red"><u>Gạch</u> <strong>Đậm</strong> <a href="https://a.com">Link</a><img src="x.png"/></p>""",
                ),
            )
            editor.onTextContextMenuItem(android.R.id.paste)
        }
        composeRule.waitForIdle()
        val pasted = emitted.last() as EssayAnswer
        assertTrue(pasted.html?.contains("<strong>Đậm</strong>") == true)
        assertFalse(pasted.html?.contains("<img") == true)
        assertFalse(pasted.html?.contains("<a") == true)
        assertFalse(pasted.html?.contains("style=") == true)
        assertFalse(pasted.html?.contains("<u") == true)
    }

    @Test
    fun essayEditorShowsLoadingPlaceholderWhenNotReady() {
        val sample = QuestionSamplesLoader.loadByKey("essay")
        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = null,
                    mode = QuestionMode.Answering,
                    essayEditorReady = false,
                )
            }
        }
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_LOADING).assertIsDisplayed()
        composeRule.onNodeWithText("Đang tải trình soạn bài viết…").assertIsDisplayed()
    }
}
