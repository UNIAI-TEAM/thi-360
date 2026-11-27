package vn.unicom.thi360.feature.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import java.math.BigDecimal
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptResultPart
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.network.generated.model.AttemptResultSegment
import vn.unicom.thi360.core.network.generated.model.AttemptResultSubject
import vn.unicom.thi360.core.network.generated.model.AttemptResultUnitStats
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ResultBreakdownTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val examCatalog by lazy { ResultTestFixtures.loadCatalogOptions("exam") }
    private val subjectCatalog by lazy { ResultTestFixtures.loadCatalogOptions("subject") }

    @Test
    fun parts() {
        val partsList = listOf(
            AttemptResultPart(
                partId = UUID.randomUUID(),
                partCode = "P_SCORED",
                partName = "Phần Toán",
                ord = 0,
                skipped = false,
                pendingReview = false,
                score = BigDecimal("18.5"),
                maxScore = BigDecimal("25"),
                stats = AttemptResultUnitStats(
                    correctCount = 15,
                    partialCount = 2,
                    wrongCount = 3,
                    blankCount = 5,
                    totalCount = 25,
                ),
            ),
            AttemptResultPart(
                partId = UUID.randomUUID(),
                partCode = "P_SKIPPED_CODE",
                partName = null,
                ord = 1,
                skipped = true,
                pendingReview = false,
                score = BigDecimal.ZERO,
                maxScore = BigDecimal("20"),
            ),
            AttemptResultPart(
                partId = UUID.randomUUID(),
                partCode = "P_ESSAY",
                partName = "Tự luận Ngữ văn",
                ord = 2,
                skipped = false,
                pendingReview = true,
                score = null,
                maxScore = BigDecimal("30"),
                stats = AttemptResultUnitStats(pendingReviewCount = 1, totalCount = 1),
            ),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PartsBreakdownSection(
                        parts = partsList,
                        attemptState = AttemptResultResponse.State.PENDING_REVIEW,
                        endReason = AttemptResultResponse.EndReason.CHOICE_TIMEOUT,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ResultTestTags.PARTS_SECTION).assertIsDisplayed()
        composeRule.onNodeWithText("Điểm từng phần").assertIsDisplayed()
        composeRule.onNodeWithText("Điểm của phần đã chấm xong là điểm riêng của phần đó, chưa phải điểm tổng.")
            .assertIsDisplayed()

        composeRule.onNodeWithTag(ResultTestTags.partCard(0)).assertIsDisplayed()
        composeRule.onNodeWithText("Phần Toán").assertIsDisplayed()
        composeRule.onNodeWithText("18,5/25").assertExists()
        composeRule.onNodeWithText("Đúng một phần: 2").assertExists()

        composeRule.onNodeWithTag(ResultTestTags.partCard(1)).performScrollTo()
        composeRule.onNodeWithText("P_SKIPPED_CODE").assertIsDisplayed()
        composeRule.onNodeWithText("Bị bỏ qua").assertIsDisplayed()
        composeRule.onNodeWithText("0/20").assertIsDisplayed()
        composeRule.onNodeWithText("Không làm phần 3: quá thời gian chọn").assertIsDisplayed()

        composeRule.onNodeWithTag(ResultTestTags.partCard(2)).performScrollTo()
        composeRule.onNodeWithText("Chấm tay").assertIsDisplayed()
        composeRule.onNodeWithText("Điểm tối đa của phần: 30.").assertIsDisplayed()
        composeRule.onNodeWithText("Chờ chấm").assertIsDisplayed()
    }

    @Test
    fun segmentsAndSubjects() {
        val segments = listOf(
            AttemptResultSegment(
                code = "SEG_1",
                name = "Đọc hiểu",
                fromPosition = 1,
                toPosition = 20,
                pendingReview = false,
                score = BigDecimal("14.5"),
                maxPoints = BigDecimal("20"),
                stats = AttemptResultUnitStats(correctCount = 14, partialCount = 1, wrongCount = 3, blankCount = 2),
            ),
            AttemptResultSegment(
                code = "SEG_ESSAY",
                name = "Viết nghị luận",
                fromPosition = 21,
                toPosition = 21,
                pendingReview = true,
                score = null,
                maxPoints = BigDecimal("10"),
                stats = AttemptResultUnitStats(pendingReviewCount = 1),
            ),
        )
        val subjects = listOf(
            AttemptResultSubject(
                code = "TOAN",
                name = "Raw Math",
                pendingReview = false,
                score = BigDecimal("8.25"),
                maxScore = BigDecimal("10"),
                stats = AttemptResultUnitStats(correctCount = 8, wrongCount = 2),
            ),
            AttemptResultSubject(
                code = "CUSTOM_SUB",
                name = "Môn chuyên đề",
                pendingReview = true,
                score = BigDecimal("99"),
                maxScore = BigDecimal("10"),
                stats = AttemptResultUnitStats(pendingReviewCount = 1),
            ),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SegmentsBreakdownSection(
                        segments = segments,
                        attemptState = AttemptResultResponse.State.PENDING_REVIEW,
                    )
                    SubjectsBreakdownSection(
                        subjects = subjects,
                        subjectCatalog = subjectCatalog,
                        attemptState = AttemptResultResponse.State.PENDING_REVIEW,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ResultTestTags.SEGMENTS_SECTION).assertIsDisplayed()
        composeRule.onNodeWithText("Đọc hiểu").assertExists()
        composeRule.onNodeWithText("Câu 1–20").assertExists()
        composeRule.onNodeWithText("14,5/20").assertExists()
        composeRule.onNodeWithText("Viết nghị luận").assertExists()

        composeRule.onNodeWithTag(ResultTestTags.SUBJECTS_SECTION).performScrollTo()
        composeRule.onNodeWithText("Toán học").assertExists()
        composeRule.onNodeWithText("8,25/10").assertExists()
        composeRule.onNodeWithText("Môn chuyên đề").assertExists()
        composeRule.onNodeWithText("99/10").assertDoesNotExist()
    }

    @Test
    fun vact() {
        val vactResult = ResultTestFixtures.loadResult("vact").copy(
            subjects = listOf(
                AttemptResultSubject(
                    code = "TOAN",
                    name = "Toán học",
                    score = BigDecimal("10"),
                    maxScore = BigDecimal("30"),
                ),
            ),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ResultScreen(
                    uiState = ResultUiState(
                        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                        loadState = LoadState.Loaded(vactResult),
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

        composeRule.onNodeWithTag(ResultTestTags.VACT_PRACTICE_BADGE).assertTextContains("Điểm luyện tập")
        composeRule.onNodeWithTag(ResultTestTags.VACT_PRACTICE_NOTE)
            .assertTextContains("Không phải điểm V-ACT chính thức")
        composeRule.onNodeWithText("Điểm luyện tập quy từ điểm các câu về thang 1.200.").assertExists()

        composeRule.onNodeWithTag(ResultTestTags.SUBJECTS_SECTION).assertDoesNotExist()

        composeRule.onNodeWithTag(ResultTestTags.CONTENT_LIST)
            .performScrollToNode(hasTestTag(ResultTestTags.VACT_COMPONENTS_SECTION))
        composeRule.onNodeWithTag(ResultTestTags.VACT_COMPONENTS_SECTION).assertExists()
        composeRule.onNodeWithText("Kết quả theo thành phần").assertExists()
        composeRule.onNodeWithText("5/30 câu đúng · 16,67%").assertExists()
        composeRule.onNodeWithTag(ResultTestTags.VACT_SUBSECTIONS_LIST).assertDoesNotExist()

        composeRule.onNodeWithTag(ResultTestTags.VACT_SUBSECTIONS_TOGGLE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ResultTestTags.VACT_SUBSECTIONS_LIST).assertExists()
        composeRule.onNodeWithText("Logic và phân tích số liệu").assertExists()
        composeRule.onNodeWithText("12 câu").assertExists()
        composeRule.onNodeWithText("Suy luận khoa học").assertExists()
        composeRule.onNodeWithText("18 câu").assertExists()
        composeRule.onNodeWithTag(ResultTestTags.VACT_SUBSECTIONS_TOGGLE)
            .assertTextContains("Ẩn chi tiết 2 tiểu mục")
    }
}
