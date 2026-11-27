package vn.unicom.thi360.feature.question

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class ReviewChoiceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun singleSampleShowsWrongPickCorrectAnswerSolutionAndHidesFlag() {
        val sample = QuestionSamplesLoader.loadByKey("single")
        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = sample.submitted,
                    mode = QuestionMode.Review(sample.review),
                    onFlag = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.FRAME_FLAG_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_OUTCOME_CHIP).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_POINTS).assertTextEquals("0 / 1 điểm")
        composeRule.onNodeWithText("Bạn chọn · Sai").assertIsDisplayed()
        composeRule.onNodeWithText("Đáp án đúng").assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_SOLUTION_BLOCK).assertIsDisplayed()
    }

    @Test
    fun singleNoKeySampleNeverFabricatesWrongTagOrCorrectAnswerTag() {
        val sample = QuestionSamplesLoader.loadByKey("singleNoKey")
        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = sample.submitted,
                    mode = QuestionMode.Review(sample.review),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeRule.onNodeWithText("Bạn chọn").assertIsDisplayed()
        composeRule.onNodeWithText("Bạn chọn · Sai").assertDoesNotExist()
        composeRule.onNodeWithText("Đáp án đúng").assertDoesNotExist()
    }

    @Test
    fun multipleSampleShowsPartialTagsIncludingNotPicked() {
        val sample = QuestionSamplesLoader.loadByKey("multiple")
        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = sample.submitted,
                    mode = QuestionMode.Review(sample.review),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeRule.onNodeWithText("Đúng một phần").assertIsDisplayed()
        composeRule.onNodeWithText("Bạn chọn").assertIsDisplayed()
        composeRule.onNodeWithText("Bạn không chọn").assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_POINTS).assertTextEquals("0,5 / 1 điểm")
    }
}
