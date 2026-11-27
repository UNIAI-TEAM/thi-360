package vn.unicom.thi360.feature.result

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
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
import vn.unicom.thi360.feature.result.review.ReviewTestTags
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.LocalResultDependenciesOverride
import vn.unicom.thi360.shell.routes.ResultRouteDependencies
import vn.unicom.thi360.shell.routes.RoutePlaceholderTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class ResultNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer
    private lateinit var connectivity: MutableConnectivityMonitor

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        ResultTestFixtures.installCatalogRoutes(server)
        connectivity = MutableConnectivityMonitor(initialOnline = true)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createDeps(
        clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    ): ResultRouteDependencies {
        val stack = ResultTestFixtures.createSignedInStack(server = server, clockMillis = clockMillis)
        val repository = ResultRepository.fromSessionStack(stack, clockMillis = clockMillis)
        return ResultRouteDependencies(
            repository = repository,
            connectivityMonitor = connectivity,
            assetSourceFactory = { _, _ -> null },
            formulaEngineFactory = { SourceBoxFormulaEngine() },
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun resultFromAttemptReplacesAttemptAndTabButtonsTriggerReload() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            200,
            ResultTestFixtures.readIndexedRaw("getResult", 200, "scored"),
        )
        val deps = createDeps()
        var navigator: AppNavigator? = null

        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            CompositionLocalProvider(LocalResultDependenciesOverride provides deps) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    navController = navController,
                    navigator = appNav,
                )
            }
        }
        val nav = checkNotNull(navigator)

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { nav.openAttempt(attemptId) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("attempt")

        composeRule.runOnIdle { nav.openResultFromAttempt(attemptId) }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ResultTestTags.TOTAL_SCORE_VALUE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ResultTestTags.screenFor(attemptId)).assertIsDisplayed()

        composeRule.onNodeWithTag(ResultTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.MyExams)).assertIsDisplayed()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { nav.openResult(attemptId) }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ResultTestTags.MY_EXAMS_BUTTON))
                .fetchSemanticsNodes().isNotEmpty()
        }

        val beforeMyExamsReload = nav.reloadCounters.value[Destination.MyExams] ?: 0L
        composeRule.onNodeWithTag(ResultTestTags.MY_EXAMS_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).assertIsSelected()
        assertEquals(beforeMyExamsReload + 1L, nav.reloadCounters.value[Destination.MyExams])

        composeRule.runOnIdle { nav.openResult(attemptId) }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ResultTestTags.HISTORY_BUTTON))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val beforeHistoryReload = nav.reloadCounters.value[Destination.History] ?: 0L
        composeRule.onNodeWithTag(ResultTestTags.HISTORY_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).assertIsSelected()
        assertEquals(beforeHistoryReload + 1L, nav.reloadCounters.value[Destination.History])
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun reviewOpensOnce() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
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
            ResultTestFixtures.readIndexedRaw("getReview", 200, "score-and-answers"),
        )
        var clickClock = 1_000L
        val deps = createDeps().copy(
            clickGuardFactory = { ResultClickGuard(debounceMillis = 500L, clockMillis = { clickClock }) },
        )
        var navigator: AppNavigator? = null

        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            CompositionLocalProvider(LocalResultDependenciesOverride provides deps) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    navController = navController,
                    navigator = appNav,
                )
            }
        }
        val nav = checkNotNull(navigator)
        composeRule.runOnIdle { nav.openResult(attemptId) }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ResultTestTags.REVIEW_BUTTON))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(ResultTestTags.REVIEW_BUTTON).performClick()
        clickClock += 50L
        composeRule.runOnIdle {
            val entries = nav.navController.currentBackStack.value
            val reviewCount = entries.count { it.destination.route?.contains("review/") == true }
            assertEquals(1, reviewCount)
        }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ReviewTestTags.SCREEN))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ReviewTestTags.screenFor(attemptId)).assertIsDisplayed()

        clickClock += 1_000L
        composeRule.onNodeWithTag(ReviewTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ResultTestTags.screenFor(attemptId)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun backFromReview() {
        val attemptId = ResultTestFixtures.ATTEMPT_UUID_1
        val initialResult = ResultTestFixtures.loadResult("scored")
        val updatedResult = initialResult.copy(displayScore = java.math.BigDecimal("4.5"))

        server.respondSequence(
            "GET",
            "/api/v1/me/attempts/$attemptId/result",
            { ContractFakeServer.jsonResponse(200, ResultTestFixtures.encodeResult(initialResult)) },
            { ContractFakeServer.jsonResponse(200, ResultTestFixtures.encodeResult(updatedResult)) },
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId/review",
            200,
            ResultTestFixtures.readIndexedRaw("getReview", 200, "full-solution"),
        )
        val deps = createDeps()
        var navigator: AppNavigator? = null

        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            CompositionLocalProvider(LocalResultDependenciesOverride provides deps) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    navController = navController,
                    navigator = appNav,
                )
            }
        }
        val nav = checkNotNull(navigator)
        composeRule.runOnIdle { nav.openResult(attemptId) }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ResultTestTags.TOTAL_SCORE_VALUE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_VALUE).assertTextContains("1/5")
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))

        composeRule.onNodeWithTag(ResultTestTags.REVIEW_BUTTON).performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ReviewTestTags.HEADER_SUBTITLE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result"))

        composeRule.onNodeWithTag(ReviewTestTags.BACK_BUTTON).performClick()
        composeRule.waitUntil(5_000L) {
            server.requestCount("GET", "/api/v1/me/attempts/$attemptId/result") >= 2
        }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag(ResultTestTags.TOTAL_SCORE_VALUE) and
                    androidx.compose.ui.test.hasText("4,5/5", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ResultTestTags.TOTAL_SCORE_VALUE).assertTextContains("4,5/5")
    }
}
