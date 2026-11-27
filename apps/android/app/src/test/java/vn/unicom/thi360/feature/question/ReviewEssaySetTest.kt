package vn.unicom.thi360.feature.question

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.feature.question.model.EssayGradingState
import vn.unicom.thi360.feature.question.model.EssayReview
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.ItemReview
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.model.ReviewLevel
import vn.unicom.thi360.feature.question.ui.QuestionSetView
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ReviewEssaySetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pendingEssayShowsManualChipPendingNoteAndCollapsesLongTextOver600Chars() {
        val sample = QuestionSamplesLoader.loadByKey("essay")
        val longSentence = "Hình tượng người lính trong văn học kháng chiến luôn sáng ngời phẩm chất anh hùng và lòng nhân ái sâu sắc. "
        val longHtml = "<p>${longSentence.repeat(14)}</p>"
        val pendingReview = ItemReview(
            outcome = ItemOutcome.PENDING_REVIEW,
            earned = null,
            max = BigDecimal(25),
            level = ReviewLevel.SCORE_AND_ANSWERS,
            essay = EssayReview(state = EssayGradingState.PENDING),
        )

        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = EssayAnswer(longHtml, 250),
                    mode = QuestionMode.Review(pendingReview),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.FRAME_MANUAL_CHIP).assertTextEquals("Chấm tay")
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_PENDING_NOTE).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_POINTS)
            .assertTextEquals("Chưa có điểm · tối đa 25 điểm")

        val toggle = composeRule.onNodeWithTag(QuestionTestTags.ESSAY_REVIEW_TOGGLE)
        toggle.assertIsDisplayed()
        composeRule.onNodeWithText("Xem toàn bộ bài viết").assertIsDisplayed()
        toggle.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Thu gọn bài viết").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gradedEssayShowsTwoColCriteriaOnlyWhenProvided() {
        val sample = QuestionSamplesLoader.loadByKey("essay")
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

        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_CRITERIA_TABLE).assertIsDisplayed()
        composeRule.onNodeWithText("Nêu đúng luận điểm").assertIsDisplayed()
        composeRule.onNodeWithText("Nhận xét của người chấm").assertIsDisplayed()
    }

    @Test
    fun essayReviewHidesCriteriaAndGraderCommentOutsideFullSolution() {
        val sample = QuestionSamplesLoader.loadByKey("essay")
        var level by mutableStateOf(ReviewLevel.SCORE_AND_ANSWERS)

        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = sample.item,
                    answer = sample.submitted,
                    mode = QuestionMode.Review(sample.review.copy(level = level)),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_CRITERIA_TABLE).assertDoesNotExist()
        composeRule.onNodeWithText("Nhận xét của người chấm").assertDoesNotExist()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_SOLUTION_BLOCK).assertDoesNotExist()

        level = ReviewLevel.SCORE_ONLY
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_CRITERIA_TABLE).assertDoesNotExist()
        composeRule.onNodeWithText("Nhận xét của người chấm").assertDoesNotExist()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_SOLUTION_BLOCK).assertDoesNotExist()
    }

    @Test
    fun questionSetMissingChildReviewOmitsOutcomeChipAndRespectsParentScoreOnly() {
        val sample = QuestionSamplesLoader.loadByKey("set")
        val setItem = sample.item as QuestionItem.QuestionSet
        val reviewWithoutSecondChild = sample.review.copy(
            level = ReviewLevel.SCORE_ONLY,
            children = sample.review.children.filterKeys { it == "demo-set-a" }
                .mapValues { (_, childRev) -> childRev.copy(level = ReviewLevel.SCORE_ONLY) },
        )

        composeRule.setContent {
            QuestionTestHost {
                QuestionSetView(
                    item = setItem,
                    answers = sample.submittedChildren,
                    mode = QuestionMode.Review(reviewWithoutSecondChild),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        val outcomeChips = composeRule.onAllNodesWithTag(QuestionTestTags.FRAME_OUTCOME_CHIP)
            .fetchSemanticsNodes()
        assertEquals(1, outcomeChips.size)
        composeRule.onNodeWithText("Đáp án đúng").assertDoesNotExist()
        composeRule.onNodeWithText("Đáp án:", substring = true).assertDoesNotExist()
    }
}
