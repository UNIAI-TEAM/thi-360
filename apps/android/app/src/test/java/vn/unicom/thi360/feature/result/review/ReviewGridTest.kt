package vn.unicom.thi360.feature.result.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import java.math.BigDecimal
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewOption
import vn.unicom.thi360.core.network.generated.model.AttemptReviewPart
import vn.unicom.thi360.core.network.generated.model.AttemptReviewQuestionBody
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.result.ResultTestFixtures
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine

@RunWith(RobolectricTestRunner::class)
class ReviewGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleModelWithAllOutcomesAndSet(): ReviewModel {
        val partId = UUID.randomUUID()
        val setId = UUID.randomUUID()
        val response = AttemptReviewResponse(
            attemptId = UUID.fromString(ResultTestFixtures.ATTEMPT_UUID_1),
            state = AttemptReviewResponse.State.SCORED,
            resultView = "FULL_SOLUTION",
            parts = listOf(AttemptReviewPart(partId = partId, partCode = "P1", ord = 0, state = "CLOSED")),
            items = listOf(
                leafItem(partId, 1, 1, AttemptReviewItem.Outcome.CORRECT, "Câu hỏi số 1"),
                AttemptReviewItem(
                    itemId = setId,
                    partId = partId,
                    partCode = "P1",
                    ord = 2,
                    questionType = AttemptReviewItem.QuestionType.QUESTION_SET,
                    content = "<p>Chùm 2-3</p>",
                    body = AttemptReviewQuestionBody(passage = "<p>Ngữ liệu 2-3</p>"),
                    outcome = null,
                    pointsMax = BigDecimal("2"),
                ),
                leafItem(partId, 2, 3, AttemptReviewItem.Outcome.CORRECT, "Con số 2", parentId = setId),
                leafItem(partId, 3, 4, AttemptReviewItem.Outcome.WRONG, "Con số 3", parentId = setId),
                leafItem(partId, 4, 5, AttemptReviewItem.Outcome.WRONG, "Câu hỏi số 4"),
                leafItem(partId, 5, 6, AttemptReviewItem.Outcome.BLANK, "Câu hỏi số 5"),
            ),
        )
        return ReviewMapper.map(response)
    }

    private fun leafItem(
        partId: UUID,
        displayNo: Int,
        ord: Int,
        outcome: AttemptReviewItem.Outcome,
        stem: String,
        parentId: UUID? = null,
    ): AttemptReviewItem = AttemptReviewItem(
        itemId = UUID.randomUUID(),
        partId = partId,
        partCode = "P1",
        parentItemId = parentId,
        displayNo = displayNo,
        ord = ord,
        questionType = AttemptReviewItem.QuestionType.SINGLE_CHOICE,
        content = "<p>$stem</p>",
        body = AttemptReviewQuestionBody(
            options = listOf(AttemptReviewOption(id = "a", content = "<p>A</p>")),
        ),
        pointsMax = BigDecimal.ONE,
        earnedPoints = if (outcome == AttemptReviewItem.Outcome.CORRECT) BigDecimal.ONE else BigDecimal.ZERO,
        outcome = outcome,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun compactPhoneShowsHorizontalStripSummaryLabelsJumpAndPrevNextBounds() {
        val model = sampleModelWithAllOutcomesAndSet()
        var activeGroup by mutableIntStateOf(0)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ReviewScreen(
                    uiState = ReviewUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(model),
                        activeGroupIndex = activeGroup,
                    ),
                    onBack = {},
                    onRetry = {},
                    onSelectPart = {},
                    onSelectGroup = { activeGroup = it },
                    onPreviousGroup = {
                        if (activeGroup > 0) {
                            activeGroup -= 1
                            activeGroup
                        } else {
                            null
                        }
                    },
                    onNextGroup = {
                        if (activeGroup < 3) {
                            activeGroup += 1
                            activeGroup
                        } else {
                            null
                        }
                    },
                    onGoToMyExams = {},
                    onGoToHistory = {},
                    formulaEngine = SourceBoxFormulaEngine(),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReviewTestTags.PALETTE_STRIP).assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.PALETTE_SIDE_PANE).assertDoesNotExist()
        composeRule.onNodeWithText("Danh sách câu").assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.PALETTE_SUMMARY)
            .assertTextContains("Đúng 2 · Đúng một phần 0 · Sai 2 · Bỏ trống 1")
        composeRule.onNodeWithTag(ReviewTestTags.PALETTE_LEGEND).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Câu 1, đúng").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Câu 2–3, đúng một phần").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Câu 4, sai").assertIsDisplayed()

        composeRule.onNodeWithTag(ReviewTestTags.PREV_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(ReviewTestTags.NEXT_BUTTON).assertIsEnabled()

        composeRule.onNodeWithTag(ReviewTestTags.paletteCell(1)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.paletteCell(1)).assertIsSelected()
        composeRule.onNodeWithTag(ReviewTestTags.PREV_BUTTON).assertIsEnabled()

        composeRule.onNodeWithTag(ReviewTestTags.paletteCell(3)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.paletteCell(3)).assertIsSelected()
        composeRule.onNodeWithTag(ReviewTestTags.NEXT_BUTTON).assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun wideScreenShowsPersistentSidePaneWhileScrollingQuestions() {
        val model = sampleModelWithAllOutcomesAndSet()
        var activeGroup by mutableIntStateOf(0)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ReviewScreen(
                    uiState = ReviewUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(model),
                        activeGroupIndex = activeGroup,
                    ),
                    onBack = {},
                    onRetry = {},
                    onSelectPart = {},
                    onSelectGroup = { activeGroup = it },
                    onPreviousGroup = { null },
                    onNextGroup = { null },
                    onGoToMyExams = {},
                    onGoToHistory = {},
                    formulaEngine = SourceBoxFormulaEngine(),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReviewTestTags.PALETTE_SIDE_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.PALETTE_STRIP).assertDoesNotExist()

        composeRule.onNodeWithTag(ReviewTestTags.QUESTION_LIST).performScrollToIndex(3)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.PALETTE_SIDE_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.paletteCell(0)).assertIsDisplayed()
    }
}
