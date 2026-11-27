package vn.unicom.thi360.feature.question

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.feature.question.model.ChoiceOptionItem
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionSetView
import vn.unicom.thi360.feature.question.ui.QuestionTestTags

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h720dp-xhdpi")
class QuestionSetViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchesBetweenSingleAndTwoColumnPreservingDraftAndEmitsChildItemIds() {
        val sample = QuestionSamplesLoader.loadByKey("set")
        val setItem = sample.item as QuestionItem.QuestionSet
        var containerWidthDp by mutableStateOf(360)
        var answers by mutableStateOf<Map<String, AnswerValue>>(emptyMap())
        val emittedIds = mutableListOf<String>()
        var rootView: View? = null

        composeRule.setContent {
            rootView = LocalView.current
            QuestionTestHost {
                Box(
                    modifier = Modifier
                        .width(containerWidthDp.dp)
                        .fillMaxHeight()
                        .let { mod ->
                            if (containerWidthDp < 840) mod.verticalScroll(rememberScrollState()) else mod
                        },
                ) {
                    QuestionSetView(
                        item = setItem,
                        answers = answers,
                        mode = QuestionMode.Answering,
                        onAnswer = { childId, value ->
                            emittedIds.add(childId)
                            answers = answers + (childId to value)
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.SET_SINGLE_COLUMN).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.choiceOption("s1"))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("demo-set-a", emittedIds.last())
        assertEquals(SingleChoiceAnswer("s1"), answers["demo-set-a"])

        val inputBeforeSwitch = findFirstEditText(rootView!!)!!
        composeRule.runOnIdle {
            inputBeforeSwitch.setText("2148,6")
        }
        composeRule.waitForIdle()
        assertEquals("demo-set-b", emittedIds.last())
        assertEquals(ShortAnswerValue("2148,6"), answers["demo-set-b"])
        assertTrue(emittedIds.none { it == setItem.id })

        containerWidthDp = 880
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(QuestionTestTags.SET_TWO_COLUMN).assertIsDisplayed()
        val passageBounds = composeRule.onNodeWithTag(QuestionTestTags.SET_PASSAGE_PANE)
            .fetchSemanticsNode().boundsInRoot
        val childrenBounds = composeRule.onNodeWithTag(QuestionTestTags.SET_CHILDREN_PANE)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(passageBounds.right <= childrenBounds.left + 1f)

        assertEquals(SingleChoiceAnswer("s1"), answers["demo-set-a"])
        assertEquals(ShortAnswerValue("2148,6"), answers["demo-set-b"])
        val inputAfterSwitch = findFirstEditText(rootView)
        assertEquals("2148,6", inputAfterSwitch?.text?.toString())
    }

    @Test
    fun twoColumnPassageStaysPinnedWhenOuterSheetScrollsAndScrollsIndependently() {
        val longPassage = (1..24).joinToString("") { idx ->
            "<p>Đoạn ngữ liệu số $idx trình bày bảng số liệu thí nghiệm vật lý chi tiết qua nhiều mốc thời gian đo khác nhau.</p>"
        }
        val tallChildren = (1..5).map { idx ->
            QuestionItem.SingleChoice(
                id = "sticky-child-$idx",
                displayNo = 30 + idx,
                stem = "<p>Câu hỏi con số $idx dựa trên ngữ liệu ở khung bên trái?</p>",
                options = listOf(
                    ChoiceOptionItem("c$idx-o1", "<p>Phương án A cho câu $idx</p>"),
                    ChoiceOptionItem("c$idx-o2", "<p>Phương án B cho câu $idx</p>"),
                    ChoiceOptionItem("c$idx-o3", "<p>Phương án C cho câu $idx</p>"),
                    ChoiceOptionItem("c$idx-o4", "<p>Phương án D cho câu $idx</p>"),
                ),
            )
        }
        val setItem = QuestionItem.QuestionSet(
            id = "sticky-set",
            displayNo = 31,
            stem = "<p>Đọc ngữ liệu và trả lời các câu 31–35.</p>",
            passage = longPassage,
            children = tallChildren,
        )

        lateinit var sheetScroll: androidx.compose.foundation.ScrollState
        lateinit var scope: CoroutineScope

        composeRule.setContent {
            sheetScroll = rememberScrollState()
            scope = rememberCoroutineScope()
            QuestionTestHost {
                Column(
                    modifier = Modifier
                        .width(880.dp)
                        .height(520.dp)
                        .verticalScroll(sheetScroll)
                        .testTag("outer_exam_sheet"),
                ) {
                    QuestionSetView(
                        item = setItem,
                        answers = emptyMap(),
                        mode = QuestionMode.Answering,
                    )
                    Spacer(modifier = Modifier.height(600.dp))
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QuestionTestTags.SET_TWO_COLUMN).assertIsDisplayed()

        val sheetBounds = composeRule.onNodeWithTag("outer_exam_sheet")
            .fetchSemanticsNode().boundsInWindow
        assertTrue("Expected outer sheet to be scrollable", sheetScroll.maxValue > 800)

        val midScroll = 700
        composeRule.runOnIdle {
            scope.launch { sheetScroll.scrollTo(midScroll) }
        }
        composeRule.waitForIdle()

        val passageMid = composeRule.onNodeWithTag(QuestionTestTags.SET_PASSAGE_PANE)
            .fetchSemanticsNode().boundsInWindow
        assertTrue(
            "Expected sticky passage top (${passageMid.top}) pinned near viewport top (${sheetBounds.top})",
            passageMid.top >= sheetBounds.top - 2f && passageMid.top <= sheetBounds.top + 12f,
        )
        assertTrue(
            "Expected sticky passage to fit inside viewport (${passageMid.bottom} <= ${sheetBounds.bottom + 2f})",
            passageMid.bottom <= sheetBounds.bottom + 2f,
        )

        val childrenBeforePassageScroll = composeRule.onNodeWithTag(QuestionTestTags.SET_CHILDREN_PANE)
            .fetchSemanticsNode().boundsInWindow
        composeRule.onNodeWithTag(QuestionTestTags.SET_PASSAGE_PANE)
            .performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val childrenAfterPassageScroll = composeRule.onNodeWithTag(QuestionTestTags.SET_CHILDREN_PANE)
            .fetchSemanticsNode().boundsInWindow
        assertTrue(
            "Scrolling inside passage pane must not move child questions",
            abs(childrenAfterPassageScroll.top - childrenBeforePassageScroll.top) <= 1f,
        )

        composeRule.runOnIdle {
            scope.launch { sheetScroll.scrollTo(sheetScroll.maxValue) }
        }
        composeRule.waitForIdle()

        val passageAtEnd = composeRule.onNodeWithTag(QuestionTestTags.SET_PASSAGE_PANE)
            .fetchSemanticsNode().boundsInWindow
        val twoColAtEnd = composeRule.onNodeWithTag(QuestionTestTags.SET_TWO_COLUMN)
            .fetchSemanticsNode().boundsInWindow
        assertTrue(
            "Sticky passage must clamp cleanly within two-column row bottom (${passageAtEnd.bottom} <= ${twoColAtEnd.bottom + 2f})",
            passageAtEnd.bottom <= twoColAtEnd.bottom + 2f,
        )
    }
}
