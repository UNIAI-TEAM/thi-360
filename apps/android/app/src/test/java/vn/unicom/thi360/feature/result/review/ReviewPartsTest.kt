package vn.unicom.thi360.feature.result.review

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewOption
import vn.unicom.thi360.core.network.generated.model.AttemptReviewPart
import vn.unicom.thi360.core.network.generated.model.AttemptReviewQuestionBody
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.result.ResultRepository
import vn.unicom.thi360.feature.result.ResultTestFixtures
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ReviewPartsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer
    private lateinit var connectivity: MutableConnectivityMonitor

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        connectivity = MutableConnectivityMonitor(initialOnline = true)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun partTabsSortByOrdSwitchLocallyHandleSkippedEmptyAndResetOnReopen() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        val part0Id = UUID.randomUUID()
        val part1Id = UUID.randomUUID()
        val part2Id = UUID.randomUUID()
        val part3Id = UUID.randomUUID()

        val multiPartResponse = AttemptReviewResponse(
            attemptId = UUID.fromString(attemptId),
            state = AttemptReviewResponse.State.SCORED,
            resultView = "FULL_SOLUTION",
            parts = listOf(
                AttemptReviewPart(
                    partId = part2Id,
                    partCode = "P2",
                    partName = "Phần bị bỏ qua",
                    ord = 2,
                    state = "SKIPPED",
                    score = BigDecimal.ZERO,
                    maxScore = BigDecimal("10"),
                ),
                AttemptReviewPart(
                    partId = part0Id,
                    partCode = "P0",
                    partName = "Phần 1 Toán",
                    ord = 0,
                    state = "CLOSED",
                    score = BigDecimal("1"),
                    maxScore = BigDecimal("1"),
                ),
                AttemptReviewPart(
                    partId = part3Id,
                    partCode = "P3",
                    partName = "Phần trống",
                    ord = 3,
                    state = "CLOSED",
                ),
                AttemptReviewPart(
                    partId = part1Id,
                    partCode = "P1",
                    partName = "Phần 2 Văn",
                    ord = 1,
                    state = "CLOSED",
                    score = BigDecimal("1"),
                    maxScore = BigDecimal("1"),
                ),
            ),
            items = listOf(
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = part0Id,
                    partCode = "P0",
                    displayNo = 1,
                    ord = 1,
                    questionType = AttemptReviewItem.QuestionType.SINGLE_CHOICE,
                    content = "<p>Nội dung Phần 1</p>",
                    body = AttemptReviewQuestionBody(
                        options = listOf(AttemptReviewOption(id = "a", content = "<p>A</p>")),
                    ),
                    outcome = AttemptReviewItem.Outcome.CORRECT,
                    pointsMax = BigDecimal.ONE,
                    earnedPoints = BigDecimal.ONE,
                ),
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = part1Id,
                    partCode = "P1",
                    displayNo = 2,
                    ord = 2,
                    questionType = AttemptReviewItem.QuestionType.SINGLE_CHOICE,
                    content = "<p>Nội dung Phần 2</p>",
                    body = AttemptReviewQuestionBody(
                        options = listOf(AttemptReviewOption(id = "a", content = "<p>A</p>")),
                    ),
                    outcome = AttemptReviewItem.Outcome.WRONG,
                    pointsMax = BigDecimal.ONE,
                    earnedPoints = BigDecimal.ZERO,
                ),
            ),
        )

        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/review",
            200,
            ResultTestFixtures.encodeReview(multiPartResponse),
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            200,
            ResultTestFixtures.readIndexedRaw("getResult", 200, "scored"),
        )

        val repo = ResultRepository.fromSessionStack(ResultTestFixtures.createSignedInStack(server = server))
        var reopenGeneration by mutableIntStateOf(0)

        composeRule.setContent {
            key(reopenGeneration) {
                val scope = rememberCoroutineScope()
                val vm = remember {
                    ReviewViewModel(
                        attemptId = attemptId,
                        repository = repo,
                        connectivityMonitor = connectivity,
                        scope = scope,
                    )
                }
                val uiState by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(vm) { vm.onAppear() }
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    ReviewScreen(
                        uiState = uiState,
                        onBack = {},
                        onRetry = vm::retry,
                        onSelectPart = vm::selectPart,
                        onSelectGroup = vm::selectGroup,
                        onPreviousGroup = vm::previousGroup,
                        onNextGroup = vm::nextGroup,
                        onGoToMyExams = {},
                        onGoToHistory = {},
                        formulaEngine = SourceBoxFormulaEngine(),
                    )
                }
            }
        }

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ReviewTestTags.PARTS_BAR))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/review"))

        composeRule.onNodeWithContentDescription("Các phần của đề").assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.partTab(0))
            .assertTextContains("Phần 1 Toán")
            .assertIsSelected()
        composeRule.onNodeWithTag(ReviewTestTags.partTab(1)).assertTextContains("Phần 2 Văn")
        composeRule.onNodeWithTag(ReviewTestTags.partTab(2))
            .assertTextContains("Phần bị bỏ qua", substring = true)
            .assertTextContains("Bị bỏ qua", substring = true)
        composeRule.onNodeWithContentDescription("Câu 1, đúng").assertIsDisplayed()

        composeRule.onNodeWithTag(ReviewTestTags.partTab(1)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.partTab(1)).assertIsSelected()
        composeRule.onNodeWithContentDescription("Câu 2, sai").assertIsDisplayed()
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/review"))

        composeRule.onNodeWithTag(ReviewTestTags.partTab(2)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.SKIPPED_PART_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("Phần này bị bỏ qua").assertIsDisplayed()
        composeRule.onNodeWithText("0/10 điểm", substring = true).assertIsDisplayed()
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/review"))

        composeRule.onNodeWithTag(ReviewTestTags.PARTS_BAR).performScrollToIndex(3)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.partTab(3)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReviewTestTags.EMPTY_PART_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("Không có câu hỏi trong phần này").assertIsDisplayed()
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/review"))

        composeRule.runOnIdle { reopenGeneration++ }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ReviewTestTags.partTab(0)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ReviewTestTags.partTab(0)).assertIsSelected()
        composeRule.onNodeWithContentDescription("Câu 1, đúng").assertIsDisplayed()
    }
}
