package vn.unicom.thi360.feature.question

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.model.ReviewLevel
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class ReviewShortClozeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shortAnswerReviewShowsSubmittedValueAcceptedAnswersAndSolution() {
        val sample = QuestionSamplesLoader.loadByKey("short")
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

        composeRule.onNodeWithTag(QuestionTestTags.SHORT_ANSWER_REVIEW_VALUE).assertIsDisplayed()
        composeRule.onNodeWithText("Đáp án:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("56 phút", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_SOLUTION_BLOCK).assertIsDisplayed()
    }

    @Test
    fun shortAnswerScoreOnlyHidesAcceptedAnswersAndSolution() {
        val sample = QuestionSamplesLoader.loadByKey("short")
        val scoreOnly = sample.review.copy(level = ReviewLevel.SCORE_ONLY)
        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = sample.submitted,
                    mode = QuestionMode.Review(scoreOnly),
                )
            }
        }

        composeRule.onNodeWithText("Đáp án:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_SOLUTION_BLOCK).assertDoesNotExist()
    }

    @Test
    fun clozeReviewShowsInlineBlankStatesAndStackedReviewTable() {
        val sample = QuestionSamplesLoader.loadByKey("cloze")
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

        composeRule.onNodeWithTag(QuestionTestTags.clozeBlank("b1")).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.clozeBlank("b2")).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.REVIEW_TABLE_STACKED).assertIsDisplayed()
        composeRule.onNodeWithText("Câu 22").assertIsDisplayed()
        composeRule.onNodeWithText("Câu 23").assertIsDisplayed()
    }
}
