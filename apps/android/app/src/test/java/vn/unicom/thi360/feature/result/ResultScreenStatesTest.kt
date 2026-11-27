package vn.unicom.thi360.feature.result

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
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
import vn.unicom.thi360.core.ui.StaleBannerTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ResultScreenStatesTest {

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

    private fun mountWithServer(
        attemptId: String = ResultTestFixtures.ATTEMPT_UUID_1,
        poller: GradingPoller = GradingPoller(),
        onContinueAttempt: (String) -> Unit = {},
        onGoToMyExams: () -> Unit = {},
        onGoToHistory: () -> Unit = {},
    ): () -> ResultViewModel {
        val stack = ResultTestFixtures.createSignedInStack(server = server)
        val repository = ResultRepository.fromSessionStack(stack)
        var capturedVm: ResultViewModel? = null

        composeRule.setContent {
            val scope = rememberCoroutineScope()
            val vm = remember {
                ResultViewModel(
                    attemptId = attemptId,
                    repository = repository,
                    connectivityMonitor = connectivity,
                    scope = scope,
                    poller = poller,
                ).also { capturedVm = it }
            }
            val uiState by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(vm) {
                vm.onInitialAppear()
            }
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ResultScreen(
                    uiState = uiState,
                    onBack = {},
                    onRetry = vm::retry,
                    onReloadFromTimeout = vm::reloadFromGradingTimeout,
                    onOpenReview = {},
                    onContinueAttempt = onContinueAttempt,
                    onGoToMyExams = onGoToMyExams,
                    onGoToHistory = onGoToHistory,
                )
            }
        }
        return { checkNotNull(capturedVm) }
    }

    @Test
    fun catalogFailureShowsRawCodes() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        server.respondJson("GET", "/api/v1/catalog/options", 500, """{"code":"INTERNAL_ERROR"}""")
        val scoredRaw = ResultTestFixtures.readIndexedRaw("getResult", 200, "scored")
        server.respondJson("GET", "/api/v1/me/attempts/$attemptId/result", 200, scoredRaw)

        mountWithServer(attemptId)

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.TOTAL_SCORE_VALUE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_VALUE).assertTextContains("1/5")
        composeRule.onNodeWithTag(ResultTestTags.EXAM_KIND_CHIP).assertTextContains("HSA")
    }

    @Test
    fun loadingPlaceholderUsesResultLoadingLabel() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        val release = CountDownLatch(1)
        ResultTestFixtures.installCatalogRoutes(server)
        server.respondDynamic("GET", "/api/v1/me/attempts/$attemptId/result") {
            release.await(5, TimeUnit.SECONDS)
            ContractFakeServer.jsonResponse(200, ResultTestFixtures.readIndexedRaw("getResult", 200, "scored"))
        }

        mountWithServer(attemptId)

        composeRule.onNodeWithTag(ResultTestTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Đang tải kết quả lượt thi").assertIsDisplayed()

        release.countDown()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.TOTAL_SCORE_VALUE))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun notFound404AndNotFinished409ShowDedicatedStates() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        ResultTestFixtures.installCatalogRoutes(server)
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            404,
            ResultTestFixtures.readIndexedRaw("getResult", 404, "attempt-not-found"),
        )

        mountWithServer(attemptId)
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.NOT_FOUND))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("MÃ LỖI 404").assertIsDisplayed()
        composeRule.onNodeWithText("Không tìm thấy kết quả lượt thi").assertIsDisplayed()
        composeRule.onNodeWithTag(ResultTestTags.MY_EXAMS_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(ResultTestTags.HISTORY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun conflict409AttemptNotFinishedContinuesAttempt() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        ResultTestFixtures.installCatalogRoutes(server)
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            409,
            ResultTestFixtures.readIndexedRaw("getResult", 409, "attempt-not-finished"),
        )
        var continuedId: String? = null
        mountWithServer(attemptId, onContinueAttempt = { continuedId = it })

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.NOT_FINISHED))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Lượt thi chưa kết thúc").assertIsDisplayed()
        composeRule.onNodeWithTag(ResultTestTags.CONTINUE_ATTEMPT_BUTTON).performClick()
        assertEquals(attemptId, continuedId)
        composeRule.onNodeWithTag(ResultTestTags.HISTORY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun server5xxMalformedAndOfflineAutoRetryRecoverCleanly() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        ResultTestFixtures.installCatalogRoutes(server)
        val scoredRaw = ResultTestFixtures.readIndexedRaw("getResult", 200, "scored")
        server.respondSequence(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            { ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR"}""") },
            { ContractFakeServer.jsonResponse(200, "{broken-json") },
            { ContractFakeServer.jsonResponse(200, scoredRaw) },
        )

        mountWithServer(attemptId)

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(LoadStateTestTags.ERROR_TITLE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE)
            .assertTextContains("Không tải được kết quả lượt thi")
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))

        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()
        composeRule.waitUntil(5_000L) {
            server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result") >= 2
        }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(LoadStateTestTags.RETRY_BUTTON))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.TOTAL_SCORE_VALUE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(3, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))
    }

    @Test
    fun offlineShowsCommonOfflineAndAutoRetriesOnReconnect() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_2
        ResultTestFixtures.installCatalogRoutes(server)
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            200,
            ResultTestFixtures.readIndexedRaw("getResult", 200, "scored"),
        )
        connectivity.setOnline(false)

        mountWithServer(attemptId)

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(LoadStateTestTags.ERROR_TITLE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE)
            .assertTextContains("Không có kết nối")
        assertEquals(0, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))

        connectivity.setOnline(true)
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.TOTAL_SCORE_VALUE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))
    }

    @Test
    fun staleKeepsOldResult() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        ResultTestFixtures.installCatalogRoutes(server)
        val scoredRaw = ResultTestFixtures.readIndexedRaw("getResult", 200, "scored")
        server.respondSequence(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            { ContractFakeServer.jsonResponse(200, scoredRaw) },
            { ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR"}""") },
            { ContractFakeServer.jsonResponse(200, scoredRaw) },
        )

        val getVm = mountWithServer(attemptId)
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.TOTAL_SCORE_VALUE))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle { getVm().onReappearFromSubRoute() }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(StaleBannerTestTags.BANNER))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_VALUE).assertTextContains("1/5")
        composeRule.onNodeWithTag(StaleBannerTestTags.BANNER)
            .assertTextContains("Không tải được kết quả lượt thi", substring = true)

        composeRule.onNodeWithTag(StaleBannerTestTags.RETRY).performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(StaleBannerTestTags.BANNER))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun reopenShowsNewScore() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        ResultTestFixtures.installCatalogRoutes(server)
        val pending = ResultTestFixtures.loadResult("pending-review")
        val scoredNow = pending.copy(
            state = vn.unicom.thi360.core.network.generated.model.AttemptResultResponse.State.SCORED,
            displayScore = BigDecimal("39.5"),
            scaleMax = BigDecimal("47.75"),
        )
        server.respondSequence(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            { ContractFakeServer.jsonResponse(200, ResultTestFixtures.encodeResult(pending)) },
            { ContractFakeServer.jsonResponse(200, ResultTestFixtures.encodeResult(scoredNow)) },
        )

        val getVm = mountWithServer(attemptId)
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.PENDING_TOTAL_BOX))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))

        composeRule.runOnIdle {
            getVm().onBackground()
            getVm().onForeground()
        }
        composeRule.waitForIdle()
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))
        composeRule.onNodeWithTag(ResultTestTags.PENDING_TOTAL_BOX).assertIsDisplayed()

        composeRule.runOnIdle { getVm().onReappearFromSubRoute() }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.TOTAL_SCORE_VALUE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_VALUE).assertTextContains("39,5/47,75")
    }

    @Test
    fun gradingPolls() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        ResultTestFixtures.installCatalogRoutes(server)
        val gradingRaw = ResultTestFixtures.readIndexedRaw("getResult", 200, "grading")
        val scoredRaw = ResultTestFixtures.readIndexedRaw("getResult", 200, "scored")

        server.respondSequence(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            { ContractFakeServer.jsonResponse(200, gradingRaw) },
            { ContractFakeServer.jsonResponse(200, scoredRaw) },
        )

        val poller = GradingPoller(intervalMillis = 150L, windowMillis = 10_000L)
        mountWithServer(attemptId, poller = poller)

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.GRADING_CARD))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("màn hình sẽ tự cập nhật", substring = true).assertIsDisplayed()

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.TOTAL_SCORE_VALUE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_VALUE).assertTextContains("1/5")
        assertEquals(2, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))
    }

    @Test
    fun reloadSendsOnce() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        ResultTestFixtures.installCatalogRoutes(server)
        val gradingRaw = ResultTestFixtures.readIndexedRaw("getResult", 200, "grading")
        val releaseReload = CountDownLatch(1)

        server.respondDynamicSequence(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            listOf(
                { ContractFakeServer.jsonResponse(200, gradingRaw) },
                {
                    releaseReload.await(5, TimeUnit.SECONDS)
                    ContractFakeServer.jsonResponse(200, gradingRaw)
                },
            ),
        )

        val poller = GradingPoller(
            clockMillis = { composeRule.mainClock.currentTime },
            intervalMillis = 100L,
            windowMillis = 50L,
        )
        mountWithServer(attemptId, poller = poller)

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.GRADING_TIMEOUT_CARD))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Đang tính điểm, bạn có thể quay lại sau").assertIsDisplayed()
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))

        composeRule.onNodeWithTag(ResultTestTags.GRADING_RELOAD_BUTTON).performClick()
        composeRule.onNodeWithTag(ResultTestTags.GRADING_RELOAD_BUTTON).performClick()
        composeRule.waitUntil(5_000L) {
            server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result") >= 2
        }
        assertEquals(2, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))

        releaseReload.countDown()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(ResultTestTags.GRADING_CARD))
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasTestTag(ResultTestTags.GRADING_TIMEOUT_CARD))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))
    }
}
