package vn.unicom.thi360.feature.question

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.feature.question.model.MatchingSideItem
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class MatchingQuestionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exclusiveMatchingDimsUsedOptionAndClearFreesItForOtherLeftItem() {
        val sample = QuestionSamplesLoader.loadByKey("matching")
        var current by mutableStateOf<AnswerValue?>(null)
        val emitted = mutableListOf<AnswerValue>()

        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = current,
                    mode = QuestionMode.Answering,
                    onAnswer = {
                        current = it
                        emitted.add(it)
                    },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.MATCH_COUNTER).assertTextEquals("Đã ghép 0/4 mục")
        composeRule.onNodeWithTag(QuestionTestTags.matchSelector("l1"))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(QuestionTestTags.MATCH_PICKER_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.matchPickerOption("r1")).performClick()
        composeRule.waitForIdle()
        assertEquals(MatchingAnswer(mapOf("l1" to "r1")), emitted.last())
        composeRule.onNodeWithTag(QuestionTestTags.MATCH_COUNTER).assertTextEquals("Đã ghép 1/4 mục")

        composeRule.onNodeWithTag(QuestionTestTags.matchSelector("l2")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QuestionTestTags.matchPickerOption("r1")).assertIsNotEnabled()
        composeRule.onNodeWithTag(QuestionTestTags.matchPickerOption("r2")).performClick()
        composeRule.waitForIdle()
        assertEquals(MatchingAnswer(linkedMapOf("l1" to "r1", "l2" to "r2")), emitted.last())

        composeRule.onNodeWithTag(QuestionTestTags.matchClear("l1"))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()
        val afterClear = emitted.last() as MatchingAnswer
        assertFalse(afterClear.pairs?.containsKey("l1") == true)
        assertEquals("r2", afterClear.pairs?.get("l2"))
    }

    @Test
    fun reusableMatchingAllowsSameRightAndRendersChemistryFormula() {
        val chemMatching = QuestionItem.Matching(
            id = "chem-match",
            displayNo = 10,
            stem = "<p>Ghép công thức hóa học</p>",
            left = listOf(
                MatchingSideItem("l1", "<p>Axit sunfuric</p>"),
                MatchingSideItem("l2", "<p>Dung dịch axit mạnh</p>"),
            ),
            right = listOf(
                MatchingSideItem("r1", """<p><span class="math-inline">\(\ce{H2SO4}\)</span></p>"""),
                MatchingSideItem("r2", """<p><span class="math-inline">\(\ce{NaOH}\)</span></p>"""),
            ),
            optionOrder = listOf("r2", "r1"),
            reuseRight = true,
        )
        var current by mutableStateOf<AnswerValue?>(null)

        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = chemMatching,
                    answer = current,
                    mode = QuestionMode.Answering,
                    onAnswer = { current = it },
                )
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.matchSelector("l1")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("""\(\ce{H2SO4}\)""", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.matchPickerOption("r1")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("""\(\ce{H2SO4}\)""", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(QuestionTestTags.matchSelector("l2")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QuestionTestTags.matchPickerOption("r1")).performClick()
        composeRule.waitForIdle()

        assertEquals(MatchingAnswer(linkedMapOf("l1" to "r1", "l2" to "r1")), current)
        composeRule.onNodeWithTag(QuestionTestTags.MATCH_COUNTER).assertTextEquals("Đã ghép 2/2 mục")
    }
}
