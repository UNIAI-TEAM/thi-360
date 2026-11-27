package vn.unicom.thi360.feature.question

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
class ReviewTrueFalseMatchingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun trueFalseNarrowReviewUsesStackedLabeledRowsWithZeroHorizontalScroll() {
        val sample = QuestionSamplesLoader.loadByKey("trueFalse")
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

        composeRule.onNodeWithTag(QuestionTestTags.REVIEW_TABLE_STACKED).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.REVIEW_TABLE_WIDE).assertDoesNotExist()
        assertTrue(composeRule.onAllNodesWithText("Bạn chọn:").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Đáp án:").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Điểm:").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(qualifiers = "w360dp-h720dp-xhdpi")
    fun trueFalseNoKeyHidesAnswerColumn() {
        val sample = QuestionSamplesLoader.loadByKey("trueFalseNoKey")
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

        composeRule.onNodeWithTag(QuestionTestTags.REVIEW_TABLE_STACKED).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Đáp án:").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Bạn chọn:").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp-xhdpi")
    fun matchingWideReviewUsesFourColumnTableAndShowsNotMatched() {
        val sample = QuestionSamplesLoader.loadByKey("matching")
        composeRule.setContent {
            QuestionTestHost {
                Box(modifier = Modifier.width(840.dp)) {
                    QuestionView(
                        item = sample.item,
                        answer = sample.submitted,
                        mode = QuestionMode.Review(sample.review),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.REVIEW_TABLE_WIDE).assertIsDisplayed()
        composeRule.onNodeWithText("Mục").assertIsDisplayed()
        composeRule.onNodeWithText("Bạn chọn").assertIsDisplayed()
        composeRule.onNodeWithText("Đáp án").assertIsDisplayed()
        composeRule.onNodeWithText("Điểm").assertIsDisplayed()
        composeRule.onNodeWithText("Chưa ghép").assertIsDisplayed()
    }
}
