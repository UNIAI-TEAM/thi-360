package vn.unicom.thi360.feature.question

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
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
}
