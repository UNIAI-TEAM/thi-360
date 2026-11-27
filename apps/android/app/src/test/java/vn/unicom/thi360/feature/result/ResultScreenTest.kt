package vn.unicom.thi360.feature.result

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptChoiceSummary
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.network.generated.model.AttemptResultUnitStats
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ResultScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val examCatalog by lazy { ResultTestFixtures.loadCatalogOptions("exam") }
    private val subjectCatalog by lazy { ResultTestFixtures.loadCatalogOptions("subject") }

    @Test
    fun headerAndInfo() {
        val base = ResultTestFixtures.loadResult("regrading").copy(
            startedAt = OffsetDateTime.parse("2026-11-26T01:05:00Z"),
            finishedAt = OffsetDateTime.parse("2026-11-26T02:20:30Z"),
            partCode = "P1",
            subjectCode = "TOAN",
            choices = linkedMapOf(
                "part-3" to AttemptChoiceSummary(optionCode = "SCIENCE", subjectCodes = listOf("LY", "HOA", "SINH")),
                "part-4" to AttemptChoiceSummary(optionCode = "ENGLISH"),
            ),
        )
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ResultScreen(
                    uiState = ResultUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(base),
                        examKinds = examCatalog,
                        subjects = subjectCatalog,
                    ),
                    onBack = {},
                    onRetry = {},
                    onReloadFromTimeout = {},
                    onOpenReview = {},
                    onContinueAttempt = {},
                    onGoToMyExams = {},
                    onGoToHistory = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Kết quả lượt thi").assertCountEquals(2)
        composeRule.onNodeWithTag(ResultTestTags.EXAM_TITLE).assertTextContains("BCA đầy đủ (thu nhỏ)")
        composeRule.onNodeWithTag(ResultTestTags.EXAM_KIND_CHIP)
            .assertTextContains("Đánh giá tuyển sinh Công an nhân dân (BCA)")
        composeRule.onNodeWithTag(ResultTestTags.SCOPE_CHIP).assertTextContains("Bài thi FULL")
        composeRule.onNodeWithTag(ResultTestTags.ATTEMPT_NO_CHIP).assertTextContains("Lần 1")
        composeRule.onNodeWithTag(ResultTestTags.STATUS_CHIP).assertTextContains("Đang chấm lại")

        composeRule.onNodeWithTag(ResultTestTags.CONTENT_LIST)
            .performScrollToNode(hasTestTag(ResultTestTags.INFO_CARD))
        composeRule.onNodeWithText("Thông tin lượt thi").assertExists()
        composeRule.onNodeWithText("08:05, 26/11/2026").assertExists()
        composeRule.onNodeWithText("09:20, 26/11/2026").assertExists()
        composeRule.onNodeWithText("1 giờ 15 phút").assertExists()
        composeRule.onNodeWithText("Khoa học: Vật lí, Hóa học, Sinh học · Tiếng Anh").assertExists()
        composeRule.onNodeWithText("Nộp bài").assertExists()
        composeRule.onNodeWithText("P1").assertExists()
    }

    @Test
    fun faultBanners() {
        val secretCompensatedMsg = "INTERNAL_RAW_FAULT_MESSAGE_MUST_NOT_RENDER"
        val secretSystemFaultMsg = "INTERNAL_RAW_SYSTEM_FAULT_SECRET"
        var activeResult by mutableStateOf(
            ResultTestFixtures.loadResult("compensated").copy(faultMessage = secretCompensatedMsg),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ResultScreen(
                    uiState = ResultUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(activeResult),
                        examKinds = examCatalog,
                        subjects = subjectCatalog,
                    ),
                    onBack = {},
                    onRetry = {},
                    onReloadFromTimeout = {},
                    onOpenReview = {},
                    onContinueAttempt = {},
                    onGoToMyExams = {},
                    onGoToHistory = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ResultTestTags.FAULT_BANNER)
            .assertIsDisplayed()
            .assertTextContains("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt", substring = true)
            .assertTextContains("Ghi chú: Transient worker fault", substring = true)
        composeRule.onNodeWithTag(ResultTestTags.FAULT_REASON, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(secretCompensatedMsg).assertDoesNotExist()

        activeResult = ResultTestFixtures.loadResult("system-fault").copy(faultMessage = secretSystemFaultMsg)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ResultTestTags.FAULT_BANNER)
            .assertIsDisplayed()
            .assertTextContains("Lượt kết thúc do lỗi hệ thống", substring = true)
            .assertTextContains("Ghi chú: Transient worker fault", substring = true)
        composeRule.onNodeWithText(secretSystemFaultMsg).assertDoesNotExist()
    }

    @Test
    fun reviewEntryMatrix() {
        val scoredTemplate = ResultTestFixtures.loadResult("scored")
        val gradingTemplate = ResultTestFixtures.loadResult("grading")
        val gradingErrorTemplate = ResultTestFixtures.loadResult("grading-error")
        val pendingTemplate = ResultTestFixtures.loadResult("pending-review")

        val states = listOf(
            AttemptResultResponse.State.GRADING,
            AttemptResultResponse.State.GRADING_ERROR,
            AttemptResultResponse.State.PENDING_REVIEW,
            AttemptResultResponse.State.SCORED,
        )
        val views = listOf("SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION")
        for (st in states) {
            for (view in views) {
                for (avail in listOf(true, false)) {
                    val expected = avail && view != "SCORE" &&
                        (st == AttemptResultResponse.State.SCORED || st == AttemptResultResponse.State.PENDING_REVIEW)
                    val sample = scoredTemplate.copy(state = st, resultView = view, reviewAvailable = avail)
                    assertEquals("canOpenReview($st, $view, $avail)", expected, ResultFormat.canOpenReview(sample))
                }
            }
        }

        var activeResult by mutableStateOf(
            scoredTemplate.copy(resultView = "SCORE_AND_ANSWERS", reviewAvailable = true),
        )
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ResultScreen(
                    uiState = ResultUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(activeResult),
                        examKinds = examCatalog,
                        subjects = subjectCatalog,
                    ),
                    onBack = {},
                    onRetry = {},
                    onReloadFromTimeout = {},
                    onOpenReview = {},
                    onContinueAttempt = {},
                    onGoToMyExams = {},
                    onGoToHistory = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ResultTestTags.CONTENT_LIST)
            .performScrollToNode(hasTestTag(ResultTestTags.REVIEW_BUTTON))
        composeRule.onNodeWithTag(ResultTestTags.REVIEW_BUTTON).assertTextContains("Xem lại bài")

        activeResult = pendingTemplate.copy(resultView = "FULL_SOLUTION", reviewAvailable = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ResultTestTags.CONTENT_LIST)
            .performScrollToNode(hasTestTag(ResultTestTags.REVIEW_BUTTON))
        composeRule.onNodeWithTag(ResultTestTags.REVIEW_BUTTON)
            .assertTextContains("Xem lại câu đã chấm tự động")
        composeRule.onNodeWithTag(ResultTestTags.PENDING_REVIEW_NOTE).assertExists()

        activeResult = scoredTemplate.copy(resultView = "SCORE", reviewAvailable = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ResultTestTags.CONTENT_LIST)
            .performScrollToNode(hasTestTag(ResultTestTags.SCORE_ONLY_NOTE))
        composeRule.onNodeWithTag(ResultTestTags.REVIEW_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(ResultTestTags.SCORE_ONLY_NOTE).assertExists()

        activeResult = gradingTemplate.copy(reviewAvailable = true, resultView = "FULL_SOLUTION")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ResultTestTags.GRADING_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_CARD).assertDoesNotExist()
        composeRule.onNodeWithTag(ResultTestTags.REVIEW_BUTTON).assertDoesNotExist()

        activeResult = gradingErrorTemplate
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ResultTestTags.GRADING_ERROR_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(ResultTestTags.HISTORY_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(ResultTestTags.MY_EXAMS_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_CARD).assertDoesNotExist()
        composeRule.onNodeWithTag(ResultTestTags.REVIEW_BUTTON).assertDoesNotExist()
    }

    @Test
    fun totalAndStats() {
        val scored = ResultTestFixtures.loadResult("scored").copy(
            displayScore = BigDecimal("7.5"),
            scaleMax = BigDecimal("10"),
            statistics = AttemptResultUnitStats(
                correctCount = 7,
                partialCount = 1,
                wrongCount = 1,
                blankCount = 1,
                pendingReviewCount = 0,
                totalCount = 10,
            ),
        )
        var activeResult by mutableStateOf(scored)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ResultScreen(
                    uiState = ResultUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(activeResult),
                        examKinds = examCatalog,
                        subjects = subjectCatalog,
                    ),
                    onBack = {},
                    onRetry = {},
                    onReloadFromTimeout = {},
                    onOpenReview = {},
                    onContinueAttempt = {},
                    onGoToMyExams = {},
                    onGoToHistory = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_VALUE).assertTextContains("7,5/10")
        composeRule.onNodeWithText("Tổng điểm các câu trong phạm vi bài làm. Câu bỏ trống được 0 điểm.")
            .assertExists()
        composeRule.onNodeWithTag(ResultTestTags.STAT_CORRECT).assertExists()
        composeRule.onNodeWithTag(ResultTestTags.STAT_PARTIAL).assertExists()
        composeRule.onNodeWithTag(ResultTestTags.STAT_WRONG).assertExists()
        composeRule.onNodeWithTag(ResultTestTags.STAT_BLANK).assertExists()
        composeRule.onNodeWithTag(ResultTestTags.STAT_PENDING).assertDoesNotExist()
        composeRule.onNodeWithText("Trên 10 câu phải làm.").assertExists()

        activeResult = ResultTestFixtures.loadResult("pending-review")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_VALUE).assertDoesNotExist()
        composeRule.onNodeWithTag(ResultTestTags.PENDING_TOTAL_BOX).assertIsDisplayed()
        composeRule.onNodeWithText("Bạn có thể rời màn hình.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ResultTestTags.CONTENT_LIST)
            .performScrollToNode(hasTestTag(ResultTestTags.STAT_PENDING))
        composeRule.onNodeWithTag(ResultTestTags.STAT_PENDING).assertExists()
        composeRule.onNodeWithText("Trên 27 câu phải làm.").assertExists()

        activeResult = ResultTestFixtures.loadResult("regrading")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ResultTestTags.CONTENT_LIST)
            .performScrollToNode(hasTestTag(ResultTestTags.TOTAL_SCORE_CARD))
        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_VALUE).assertTextContains("45,5/47,75")
        composeRule.onNodeWithTag(ResultTestTags.REGRADING_NOTE).assertIsDisplayed()
        composeRule.onNodeWithText("Điểm bên trên là điểm trước khi chấm lại", substring = true).assertExists()
    }
}
