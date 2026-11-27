package vn.unicom.thi360.feature.result.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import java.math.BigDecimal
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.network.generated.model.AttemptReviewExpectedAnswer
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewMatchingKey
import vn.unicom.thi360.core.network.generated.model.AttemptReviewMatchingSide
import vn.unicom.thi360.core.network.generated.model.AttemptReviewOption
import vn.unicom.thi360.core.network.generated.model.AttemptReviewPart
import vn.unicom.thi360.core.network.generated.model.AttemptReviewQuestionBody
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.network.generated.model.AttemptReviewStatement
import vn.unicom.thi360.core.network.generated.model.AttemptReviewTrueFalseKey
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.ReviewGradingDetail
import vn.unicom.thi360.core.network.generated.model.ReviewMatchingPairResult
import vn.unicom.thi360.core.network.generated.model.ReviewStatementResult
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.result.ResultTestFixtures
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ReviewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerAndBanners() {
        val scoreAndAnswers = ReviewMapper.map(ResultTestFixtures.loadReview("score-and-answers"))
        val scoredHeader = ResultTestFixtures.loadResult("scored")
        val pendingModel = ReviewMapper.map(ResultTestFixtures.loadReview("pending-review"))
        val regradingHeader = ResultTestFixtures.loadResult("regrading")
        val pendingHeader = ResultTestFixtures.loadResult("pending-review")

        var activeModel by mutableStateOf(scoreAndAnswers)
        var activeHeader by mutableStateOf<AttemptResultResponse?>(scoredHeader)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ReviewScreen(
                    uiState = ReviewUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(activeModel),
                        headerResult = activeHeader,
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

        composeRule.onNodeWithText("Xem lại bài làm").assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.HEADER_SUBTITLE)
            .assertTextContains("HSA theo phần Toán (thu nhỏ) · Bài thi theo phần · Lần 1 · Tổng điểm: 1/5")
        composeRule.onNodeWithTag(ReviewTestTags.SCORE_ONLY_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Không hiển thị lời giải cho mức quyền lợi hiện tại.").assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.PENDING_BANNER).assertDoesNotExist()

        activeModel = pendingModel
        activeHeader = regradingHeader
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReviewTestTags.PENDING_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Lượt thi còn câu tự luận đang chờ chấm", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.HEADER_SUBTITLE)
            .assertTextContains("Tổng điểm: 45,5/47,75", substring = true)

        activeHeader = pendingHeader
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.HEADER_SUBTITLE)
            .assertTextContains("BCA đầy đủ (thu nhỏ) · Bài thi FULL · Lần 1")
    }

    @Test
    fun questionContent() {
        val partId = UUID.randomUUID()
        val setId = UUID.randomUUID()
        val response = AttemptReviewResponse(
            attemptId = UUID.fromString(ResultTestFixtures.ATTEMPT_UUID_1),
            state = AttemptReviewResponse.State.SCORED,
            resultView = "FULL_SOLUTION",
            parts = listOf(AttemptReviewPart(partId = partId, partCode = "P1", partName = "Phần 1", ord = 0, state = "CLOSED")),
            items = listOf(
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = partId,
                    partCode = "P1",
                    displayNo = 1,
                    ord = 1,
                    questionType = AttemptReviewItem.QuestionType.SINGLE_CHOICE,
                    content = "<p>Câu trắc nghiệm sai</p>",
                    body = AttemptReviewQuestionBody(
                        options = listOf(
                            AttemptReviewOption(id = "a", content = "<p>Phương án A</p>"),
                            AttemptReviewOption(id = "b", content = "<p>Phương án B</p>"),
                        ),
                    ),
                    studentAnswer = SingleChoiceAnswer(optionId = "a"),
                    optionOrder = listOf("b", "a"),
                    pointsMax = BigDecimal("2"),
                    earnedPoints = null,
                    outcome = AttemptReviewItem.Outcome.WRONG,
                    expectedAnswer = AttemptReviewExpectedAnswer(expectedOptionIds = listOf("b")),
                ),
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = partId,
                    partCode = "P1",
                    displayNo = 2,
                    ord = 2,
                    questionType = AttemptReviewItem.QuestionType.MATCHING,
                    content = "<p>Câu ghép đôi</p>",
                    body = AttemptReviewQuestionBody(
                        left = listOf(
                            AttemptReviewMatchingSide(id = "L1", content = "<p>Trái 1</p>"),
                            AttemptReviewMatchingSide(id = "L2", content = "<p>Trái 2</p>"),
                        ),
                        right = listOf(
                            AttemptReviewMatchingSide(id = "R1", content = "<p>Phải 1</p>"),
                            AttemptReviewMatchingSide(id = "R2", content = "<p>Phải 2</p>"),
                        ),
                    ),
                    studentAnswer = MatchingAnswer(pairs = mapOf("L1" to "R1")),
                    pointsMax = BigDecimal("2"),
                    earnedPoints = BigDecimal("1"),
                    outcome = AttemptReviewItem.Outcome.PARTIAL,
                    gradingDetail = ReviewGradingDetail(
                        pairResults = listOf(
                            ReviewMatchingPairResult(leftId = "L1", matched = true, pointsEarned = BigDecimal("1")),
                            ReviewMatchingPairResult(leftId = "L2", matched = false, pointsEarned = BigDecimal.ZERO),
                        ),
                    ),
                    expectedAnswer = AttemptReviewExpectedAnswer(
                        matchingKeys = listOf(
                            AttemptReviewMatchingKey(leftId = "L1", rightId = "R1"),
                            AttemptReviewMatchingKey(leftId = "L2", rightId = "R2"),
                        ),
                    ),
                ),
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = partId,
                    partCode = "P1",
                    displayNo = 3,
                    ord = 3,
                    questionType = AttemptReviewItem.QuestionType.TRUE_FALSE,
                    content = "<p>Câu Đúng Sai</p>",
                    body = AttemptReviewQuestionBody(
                        statements = listOf(
                            AttemptReviewStatement(id = "S1", content = "<p>Phát biểu 1</p>"),
                            AttemptReviewStatement(id = "S2", content = "<p>Phát biểu 2</p>"),
                        ),
                    ),
                    studentAnswer = TrueFalseAnswer(statements = mapOf("S1" to true, "S2" to false)),
                    pointsMax = BigDecimal("1"),
                    earnedPoints = BigDecimal("0.5"),
                    outcome = AttemptReviewItem.Outcome.PARTIAL,
                    gradingDetail = ReviewGradingDetail(
                        statementResults = listOf(
                            ReviewStatementResult(id = "S1", answered = true, matched = true),
                            ReviewStatementResult(id = "S2", answered = true, matched = false),
                        ),
                    ),
                    expectedAnswer = AttemptReviewExpectedAnswer(
                        trueFalseKeys = listOf(
                            AttemptReviewTrueFalseKey(statementId = "S1", expected = true),
                            AttemptReviewTrueFalseKey(statementId = "S2", expected = true),
                        ),
                    ),
                ),
                AttemptReviewItem(
                    itemId = setId,
                    partId = partId,
                    partCode = "P1",
                    ord = 4,
                    questionType = AttemptReviewItem.QuestionType.QUESTION_SET,
                    content = "<p>Chùm câu hỏi</p>",
                    body = AttemptReviewQuestionBody(passage = "<p>Đoạn ngữ liệu chùm câu</p>"),
                    pointsMax = BigDecimal("1"),
                    earnedPoints = BigDecimal("1"),
                    outcome = AttemptReviewItem.Outcome.CORRECT,
                ),
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = partId,
                    partCode = "P1",
                    parentItemId = setId,
                    displayNo = 4,
                    ord = 5,
                    questionType = AttemptReviewItem.QuestionType.SINGLE_CHOICE,
                    content = "<p>Câu con trong chùm</p>",
                    body = AttemptReviewQuestionBody(
                        options = listOf(
                            AttemptReviewOption(id = "x", content = "<p>Đúng X</p>"),
                            AttemptReviewOption(id = "y", content = "<p>Sai Y</p>"),
                        ),
                    ),
                    studentAnswer = SingleChoiceAnswer(optionId = "x"),
                    pointsMax = BigDecimal("1"),
                    earnedPoints = BigDecimal("1"),
                    outcome = AttemptReviewItem.Outcome.CORRECT,
                    expectedAnswer = AttemptReviewExpectedAnswer(expectedOptionIds = listOf("x")),
                ),
            ),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ReviewScreen(
                    uiState = ReviewUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(ReviewMapper.map(response)),
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

        composeRule.onNodeWithTag(ReviewTestTags.questionCard(0)).assertIsDisplayed()
        composeRule.onNodeWithText("Chưa có điểm · tối đa 2 điểm").assertExists()
        composeRule.onNodeWithText("Bạn chọn · Sai").assertExists()
        composeRule.onNodeWithText("Đáp án đúng").assertExists()

        composeRule.onNodeWithTag(ReviewTestTags.QUESTION_LIST).performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.questionCard(1)).assertIsDisplayed()
        composeRule.onNodeWithText("1 / 2 điểm").assertExists()

        composeRule.onNodeWithTag(ReviewTestTags.QUESTION_LIST).performScrollToIndex(2)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.questionCard(2)).assertIsDisplayed()
        composeRule.onNodeWithText("0,5 / 1 điểm").assertExists()

        composeRule.onNodeWithTag(ReviewTestTags.QUESTION_LIST).performScrollToIndex(3)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.questionCard(3)).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.SET_ROOT).assertExists()
        composeRule.onNodeWithTag(QuestionTestTags.SET_PASSAGE_PANE).assertExists()
    }
}
