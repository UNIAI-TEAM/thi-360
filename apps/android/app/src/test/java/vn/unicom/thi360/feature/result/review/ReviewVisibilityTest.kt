package vn.unicom.thi360.feature.result.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import java.math.BigDecimal
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptReviewEssayCriterionScore
import vn.unicom.thi360.core.network.generated.model.AttemptReviewEssayFeedback
import vn.unicom.thi360.core.network.generated.model.AttemptReviewExpectedAnswer
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewOption
import vn.unicom.thi360.core.network.generated.model.AttemptReviewPart
import vn.unicom.thi360.core.network.generated.model.AttemptReviewQuestionBody
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.result.ResultTestFixtures
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ReviewVisibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildRichVisibilityResponse(resultView: String): AttemptReviewResponse {
        val partId = UUID.randomUUID()
        val setId = UUID.randomUUID()
        return AttemptReviewResponse(
            attemptId = UUID.fromString(ResultTestFixtures.ATTEMPT_UUID_1),
            state = AttemptReviewResponse.State.SCORED,
            resultView = resultView,
            parts = listOf(AttemptReviewPart(partId = partId, partCode = "P1", ord = 0, state = "CLOSED")),
            items = listOf(
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = partId,
                    partCode = "P1",
                    displayNo = 1,
                    ord = 1,
                    questionType = AttemptReviewItem.QuestionType.SINGLE_CHOICE,
                    content = "<p>Câu MCQ có lời giải</p>",
                    body = AttemptReviewQuestionBody(
                        options = listOf(
                            AttemptReviewOption(id = "a", content = "<p>Đáp án A</p>"),
                            AttemptReviewOption(id = "b", content = "<p>Đáp án B</p>"),
                        ),
                    ),
                    studentAnswer = SingleChoiceAnswer(optionId = "a"),
                    pointsMax = BigDecimal.ONE,
                    earnedPoints = BigDecimal.ONE,
                    outcome = AttemptReviewItem.Outcome.CORRECT,
                    expectedAnswer = AttemptReviewExpectedAnswer(expectedOptionIds = listOf("a")),
                    solution = "<p>Lời giải chi tiết câu 1</p>",
                ),
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = partId,
                    partCode = "P1",
                    displayNo = 2,
                    ord = 2,
                    questionType = AttemptReviewItem.QuestionType.ESSAY,
                    content = "<p>Bài tự luận đã chấm</p>",
                    studentAnswer = EssayAnswer(html = "<p>Nội dung bài viết học viên</p>", wordCount = 5),
                    pointsMax = BigDecimal("10"),
                    earnedPoints = BigDecimal("8.5"),
                    outcome = AttemptReviewItem.Outcome.PARTIAL,
                    essayFeedback = AttemptReviewEssayFeedback(
                        comment = "Lập luận mạch lạc, dẫn chứng tốt.",
                        criteria = listOf(
                            AttemptReviewEssayCriterionScore(
                                id = "Bố cục",
                                points = BigDecimal("4.5"),
                                comment = "Rõ ràng ba phần",
                            ),
                            AttemptReviewEssayCriterionScore(
                                id = null,
                                points = null,
                                comment = null,
                            ),
                        ),
                    ),
                ),
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = partId,
                    partCode = "P1",
                    displayNo = 3,
                    ord = 3,
                    questionType = AttemptReviewItem.QuestionType.ESSAY,
                    content = "<p>Bài tự luận bỏ trống</p>",
                    studentAnswer = null,
                    pointsMax = BigDecimal("5"),
                    earnedPoints = BigDecimal.ZERO,
                    outcome = AttemptReviewItem.Outcome.BLANK,
                    essayFeedback = AttemptReviewEssayFeedback(
                        comment = "HIDDEN_BLANK_ESSAY_COMMENT",
                        criteria = listOf(
                            AttemptReviewEssayCriterionScore(
                                id = "HIDDEN_BLANK_CRITERION",
                                points = BigDecimal.ZERO,
                                comment = "HIDDEN_BLANK_CRIT_COMMENT",
                            ),
                        ),
                    ),
                ),
                AttemptReviewItem(
                    itemId = setId,
                    partId = partId,
                    partCode = "P1",
                    ord = 4,
                    questionType = AttemptReviewItem.QuestionType.QUESTION_SET,
                    content = "<p>Chùm tự luận</p>",
                    body = AttemptReviewQuestionBody(passage = "<p>Ngữ liệu chùm tự luận</p>"),
                    pointsMax = BigDecimal("5"),
                    earnedPoints = BigDecimal("4"),
                    outcome = AttemptReviewItem.Outcome.PARTIAL,
                ),
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = partId,
                    partCode = "P1",
                    parentItemId = setId,
                    displayNo = 4,
                    ord = 5,
                    questionType = AttemptReviewItem.QuestionType.ESSAY,
                    content = "<p>Câu tự luận con</p>",
                    studentAnswer = EssayAnswer(html = "<p>Bài viết câu con</p>", wordCount = 4),
                    pointsMax = BigDecimal("5"),
                    earnedPoints = BigDecimal("4"),
                    outcome = AttemptReviewItem.Outcome.PARTIAL,
                    essayFeedback = AttemptReviewEssayFeedback(
                        comment = "Nhận xét câu con",
                        criteria = listOf(
                            AttemptReviewEssayCriterionScore(
                                id = "Nội dung con",
                                points = BigDecimal("4"),
                                comment = "Đạt yêu cầu",
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun scoreAndAnswersStripsSolutionCommentsAndCriteriaInMapperAndUi() {
        val model = ReviewMapper.map(buildRichVisibilityResponse("SCORE_AND_ANSWERS"))
        val groups = model.parts.single().groups

        assertNull(groups[0].mode.review.solutionHtml)
        assertNull(groups[1].mode.review.essay?.comment)
        assertTrue(groups[1].essayCriteriaBlocks.isEmpty())
        assertTrue(groups[3].essayCriteriaBlocks.isEmpty())

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ReviewScreen(
                    uiState = ReviewUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(model),
                    ),
                    onBack = {},
                    onRetry = {},
                    onSelectPart = {},
                    onSelectGroup = {},
                    onPreviousGroup = { null },
                    onNextGroup = { null },
                    onGoToMyExams = {},
                    onGoToHistory = {},
                    formulaEngine = SourceBoxFormulaEngine(),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(QuestionTestTags.FRAME_SOLUTION_BLOCK).assertDoesNotExist()
        composeRule.onNodeWithTag(ReviewTestTags.QUESTION_LIST).performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Lập luận mạch lạc, dẫn chứng tốt.").assertDoesNotExist()
        composeRule.onNodeWithText("Điểm từng tiêu chí").assertDoesNotExist()
    }

    @Test
    fun fullSolutionShowsSolutionCriteriaWithFallbacksChildHeadingAndHidesBlankEssayFeedback() {
        val model = ReviewMapper.map(buildRichVisibilityResponse("FULL_SOLUTION"))
        val groups = model.parts.single().groups

        assertEquals("<p>Lời giải chi tiết câu 1</p>", groups[0].mode.review.solutionHtml)
        assertEquals("Lập luận mạch lạc, dẫn chứng tốt.", groups[1].mode.review.essay?.comment)
        assertEquals(1, groups[1].essayCriteriaBlocks.size)
        assertNull(groups[2].mode.review.essay?.comment)
        assertTrue(groups[2].essayCriteriaBlocks.isEmpty())
        assertEquals(4, groups[3].essayCriteriaBlocks.single().childDisplayNo)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ReviewScreen(
                    uiState = ReviewUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(model),
                    ),
                    onBack = {},
                    onRetry = {},
                    onSelectPart = {},
                    onSelectGroup = {},
                    onPreviousGroup = { null },
                    onNextGroup = { null },
                    onGoToMyExams = {},
                    onGoToHistory = {},
                    formulaEngine = SourceBoxFormulaEngine(),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(QuestionTestTags.FRAME_SOLUTION_BLOCK).assertExists()

        composeRule.onNodeWithTag(ReviewTestTags.QUESTION_LIST).performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Lập luận mạch lạc, dẫn chứng tốt.").assertExists()
        composeRule.onNodeWithText("Điểm từng tiêu chí").assertExists()
        composeRule.onNodeWithText("Bố cục").assertExists()
        composeRule.onNodeWithText("4,5 điểm").assertExists()
        composeRule.onNodeWithText("Rõ ràng ba phần").assertExists()
        composeRule.onNodeWithText("Tiêu chí 2").assertExists()

        composeRule.onNodeWithTag(ReviewTestTags.QUESTION_LIST).performScrollToIndex(2)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chưa trả lời").assertExists()
        composeRule.onNodeWithText("HIDDEN_BLANK_ESSAY_COMMENT").assertDoesNotExist()
        composeRule.onNodeWithText("HIDDEN_BLANK_CRITERION").assertDoesNotExist()

        composeRule.onNodeWithTag(ReviewTestTags.QUESTION_LIST).performScrollToIndex(3)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Điểm từng tiêu chí · Câu 4").assertExists()
        composeRule.onNodeWithText("Nội dung con").assertExists()
    }
}
