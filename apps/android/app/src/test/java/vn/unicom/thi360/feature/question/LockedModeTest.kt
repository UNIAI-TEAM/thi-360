package vn.unicom.thi360.feature.question

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.nio.file.Files
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.feature.question.model.AnswerFormat
import vn.unicom.thi360.feature.question.model.ChoiceOptionItem
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.EssayEditText
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.ui.RichContentTestTags

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class LockedModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lockedSingleChoiceIgnoresTapsAndHidesFlagWhileKeepingImageZoomActive() {
        val samplePng = requireNotNull(javaClass.classLoader?.getResourceAsStream("richcontent/sample.png"))
            .use { it.readBytes() }
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/png")
                .setBody(Buffer().write(samplePng)),
        )
        server.start()
        val cacheDir = Files.createTempDirectory("thi360-locked-zoom-").toFile()
        val assets = AttemptAssetSource(
            attemptId = "00000000-0000-7000-8000-000000000001",
            baseUrl = server.url("/").toString(),
            client = OkHttpClient.Builder().cache(null).build(),
            cacheDir = cacheDir,
        )

        try {
            val item = QuestionItem.SingleChoice(
                id = "locked-sc",
                displayNo = 5,
                stem = """<p>Xem hình</p><figure class="image" data-width="medium" data-zoom="true"><img src="/api/v1/me/attempts/00000000-0000-7000-8000-000000000001/assets/00000000-0000-7000-8000-000000000099" alt="Minh họa" data-asset-id="00000000-0000-7000-8000-000000000099" /></figure>""",
                options = listOf(
                    ChoiceOptionItem("a", "<p>Đáp án A</p>"),
                    ChoiceOptionItem("b", "<p>Đáp án B</p>"),
                ),
            )
            var emittedCount = 0

            composeRule.setContent {
                QuestionTestHost {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        QuestionView(
                            item = item,
                            answer = SingleChoiceAnswer("a"),
                            mode = QuestionMode.Locked,
                            onAnswer = { emittedCount += 1 },
                            onFlag = { emittedCount += 1 },
                            assets = assets,
                        )
                    }
                }
            }

            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithTag(RichContentTestTags.IMAGE_CONTENT)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(QuestionTestTags.FRAME_FLAG_BUTTON).assertDoesNotExist()
            composeRule.onNodeWithTag(QuestionTestTags.choiceOption("b")).performClick()
            composeRule.waitForIdle()
            assertEquals(0, emittedCount)

            composeRule.onNodeWithTag(RichContentTestTags.IMAGE_CONTENT).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(RichContentTestTags.IMAGE_ZOOM_DIALOG).assertIsDisplayed()
        } finally {
            assets.purgeAll()
            cacheDir.deleteRecursively()
            server.shutdown()
        }
    }

    @Test
    fun lockedEssayBlocksKeyboardAndEditingWhileKeepingTextSelectableAndToolbarDisabled() {
        val sample = QuestionSamplesLoader.loadByKey("essay")
        var emittedCount = 0
        var rootView: View? = null

        composeRule.setContent {
            rootView = LocalView.current
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = EssayAnswer("<p>Nội dung bài làm đã lưu</p>", 6),
                    mode = QuestionMode.Locked,
                    onAnswer = { emittedCount += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_BOLD).assertIsNotEnabled()
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_ITALIC).assertIsNotEnabled()
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_BULLET).assertIsNotEnabled()
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_ORDERED).assertIsNotEnabled()
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_UNDO).assertIsNotEnabled()
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_BTN_REDO).assertIsNotEnabled()

        val editor = findFirstEditText(rootView!!) as EssayEditText
        assertNull(editor.keyListener)
        assertFalse(editor.showSoftInputOnFocus)
        assertTrue(editor.isTextSelectable)

        composeRule.runOnIdle {
            editor.toggleBold()
            editor.pasteHtmlContent("<p>Chèn lén</p>")
        }
        composeRule.waitForIdle()
        assertEquals(0, emittedCount)
        assertEquals("Nội dung bài làm đã lưu", editor.text.toString())
    }

    @Test
    fun lockedShortAnswerAndMatchingBlockEditsAndPicker() {
        val shortItem = QuestionItem.ShortAnswer(
            id = "locked-sa",
            displayNo = 9,
            stem = "<p>Nhập ion</p>",
            answerFormat = AnswerFormat.CHEM_FORMULA,
        )
        val matchSample = QuestionSamplesLoader.loadByKey("matching")
        var emitted = 0

        composeRule.setContent {
            QuestionTestHost {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    QuestionView(
                        item = shortItem,
                        answer = null,
                        mode = QuestionMode.Locked,
                        onAnswer = { emitted += 1 },
                    )
                    QuestionView(
                        item = matchSample.item,
                        answer = null,
                        mode = QuestionMode.Locked,
                        onAnswer = { emitted += 1 },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.symbolKey("plus")).performClick()
        composeRule.onNodeWithTag(QuestionTestTags.matchSelector("l1")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QuestionTestTags.MATCH_PICKER_DIALOG).assertDoesNotExist()
        assertEquals(0, emitted)
    }
}
