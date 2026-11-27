package vn.unicom.thi360.feature.result.review

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadStateTestTags
import vn.unicom.thi360.feature.result.ResultRepository
import vn.unicom.thi360.feature.result.ResultTestFixtures
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ReviewScreenStatesTest {

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

    private fun mountReview(
        attemptId: String = ResultTestFixtures.ATTEMPT_UUID_1,
        repository: ResultRepository? = null,
        onBack: () -> Unit = {},
    ) {
        val repo = repository ?: ResultRepository.fromSessionStack(
            ResultTestFixtures.createSignedInStack(server = server),
        )
        composeRule.setContent {
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
            LaunchedEffect(vm) {
                vm.onAppear()
            }
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ReviewScreen(
                    uiState = uiState,
                    onBack = onBack,
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

    @Test
    fun callsReviewOnceWithoutPartAndReusesFreshResultCache() = runTest {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        var now = 10_000L
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            200,
            ResultTestFixtures.readIndexedRaw("getResult", 200, "scored"),
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/review",
            200,
            ResultTestFixtures.readIndexedRaw("getReview", 200, "full-solution"),
        )

        val stack = ResultTestFixtures.createSignedInStack(server = server, clockMillis = { now })
        val repo = ResultRepository.fromSessionStack(stack, clockMillis = { now })
        repo.result(attemptId)
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))

        now += 15_000L
        mountReview(attemptId = attemptId, repository = repo)

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ReviewTestTags.QUESTION_LIST))
                .fetchSemanticsNodes().isNotEmpty()
        }

        val reviewReqs = server.requests("GET", "/api/v1/me/attempts/$attemptId/review")
        assertEquals(1, reviewReqs.size)
        assertNull("Review call must not send part query param", reviewReqs.single().requestUrl?.queryParameter("part"))
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))
        composeRule.onNodeWithTag(ReviewTestTags.HEADER_SUBTITLE).assertIsDisplayed()
    }

    @Test
    fun loadingPlaceholderAndResultFailureFallbackStillRenderQuestions() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        val release = CountDownLatch(1)
        server.respondDynamic("GET", "/api/v1/me/attempts/$attemptId/review") {
            release.await(5, TimeUnit.SECONDS)
            ContractFakeServer.jsonResponse(200, ResultTestFixtures.readIndexedRaw("getReview", 200, "full-solution"))
        }
        server.respondJson("GET", "/api/v1/me/attempts/$attemptId/result", 500, """{"code":"INTERNAL_ERROR"}""")

        mountReview(attemptId = attemptId)

        composeRule.onNodeWithTag(ReviewTestTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Đang tải bài làm để xem lại").assertIsDisplayed()

        release.countDown()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ReviewTestTags.QUESTION_LIST))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Xem lại bài làm").assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.HEADER_SUBTITLE).assertDoesNotExist()
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))
    }

    @Test
    fun forbidden403ReviewNotIncludedShowsA94CopyAndBackButton() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/review",
            403,
            ResultTestFixtures.readIndexedRaw("getReview", 403, "review-not-included"),
        )
        var backed = false
        mountReview(attemptId = attemptId, onBack = { backed = true })

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ReviewTestTags.NOT_INCLUDED))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Bài thi này không có phần xem lại bài làm").assertIsDisplayed()
        composeRule.onNodeWithText("ở màn hình kết quả", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.BACK_TO_RESULT_BUTTON).performClick()
        assertTrue(backed)
    }

    @Test
    fun conflict409ReviewNotAvailableShowsCopyAndBackButton() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/review",
            409,
            ResultTestFixtures.readIndexedRaw("getReview", 409, "review-not-available"),
        )
        var backed = false
        mountReview(attemptId = attemptId, onBack = { backed = true })

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ReviewTestTags.NOT_AVAILABLE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Chưa thể xem lại bài làm").assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.BACK_TO_RESULT_BUTTON).performClick()
        assertTrue(backed)
    }

    @Test
    fun notFound404ShowsError404AndTwoTabButtons() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/review",
            404,
            ResultTestFixtures.readIndexedRaw("getReview", 404, "attempt-not-found"),
        )
        mountReview(attemptId = attemptId)

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ReviewTestTags.NOT_FOUND))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("MÃ LỖI 404").assertIsDisplayed()
        composeRule.onNodeWithText("Không tìm thấy bài làm").assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.MY_EXAMS_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(ReviewTestTags.HISTORY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun server5xxRetriesAndOfflineAutoRetriesOnReconnect() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        server.respondSequence(
            "GET",
            "/api/v1/me/attempts/$attemptId/review",
            { ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR"}""") },
            { ContractFakeServer.jsonResponse(200, ResultTestFixtures.readIndexedRaw("getReview", 200, "score-and-answers")) },
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            200,
            ResultTestFixtures.readIndexedRaw("getResult", 200, "scored"),
        )

        connectivity.setOnline(false)
        mountReview(attemptId = attemptId)

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(LoadStateTestTags.ERROR_TITLE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE)
            .assertTextContains("Không có kết nối")

        connectivity.setOnline(true)
        composeRule.waitUntil(5_000L) {
            server.requestCount("GET", "/api/v1/me/attempts/$attemptId/review") >= 1
        }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(LoadStateTestTags.ERROR_TITLE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE)
            .assertTextContains("Không tải được bài làm")

        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ReviewTestTags.QUESTION_LIST))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/review"))
    }

    @Test
    fun forbidden403WithoutReviewNotIncludedCodeShowsRetryableError() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_2
        server.respondSequence(
            "GET",
            "/api/v1/me/attempts/$attemptId/review",
            { ContractFakeServer.jsonResponse(403, "{}") },
            { ContractFakeServer.jsonResponse(403, """{"code":"FORBIDDEN"}""") },
            { ContractFakeServer.jsonResponse(200, ResultTestFixtures.readIndexedRaw("getReview", 200, "full-solution")) },
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            200,
            ResultTestFixtures.readIndexedRaw("getResult", 200, "scored"),
        )

        mountReview(attemptId = attemptId)

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(LoadStateTestTags.ERROR_TITLE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ReviewTestTags.NOT_INCLUDED).assertDoesNotExist()
        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE)
            .assertTextContains("Không tải được bài làm")
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()

        composeRule.waitUntil(5_000L) {
            server.requestCount("GET", "/api/v1/me/attempts/$attemptId/review") >= 2
        }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(LoadStateTestTags.RETRY_BUTTON))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ReviewTestTags.NOT_INCLUDED).assertDoesNotExist()
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ReviewTestTags.QUESTION_LIST))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(3, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/review"))
    }
}
