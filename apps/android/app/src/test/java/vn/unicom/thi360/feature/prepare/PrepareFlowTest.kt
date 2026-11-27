package vn.unicom.thi360.feature.prepare

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.shell.routes.LocalPrepareStartContextStoreOverride
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class PrepareFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer
    private lateinit var storeRoot: File
    private lateinit var storeProvider: AccountScopedStoreProvider

    private val entitlementId = UUID.fromString("00000000-0000-7000-8000-000000000001")
    private val preparePath = "/api/v1/me/entitlements/$entitlementId/prepare"

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = ContractFakeServer().start()
        storeRoot = Files.createTempDirectory("thi360-prepare-flow-").toFile()
        storeProvider = AccountScopedStoreProvider(storeRoot) { "acc-flow" }
        app.setTestOverrides(connectivityMonitor = MutableConnectivityMonitor(initialOnline = true))
    }

    @After
    fun tearDown() {
        PrepareSessionMemory.clearAllForTesting()
        AppSessionContainer.setTestStack(app, null)
        app.setTestOverrides()
        server.shutdown()
        storeRoot.deleteRecursively()
    }

    private fun createActiveStack() = Thi360ApiFactory.createSessionStack(
        baseUrl = server.url("/"),
        environment = Environment.DEV,
        tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "flow-access",
                refreshToken = "flow-refresh",
                accessExpiresIn = 3600,
                refreshExpiresIn = 86400,
                mustChangePassword = false,
                accountId = "acc-flow",
                username = "hv01",
            ),
        ),
        localeProvider = { AppLocale.VI },
    )

    @Test
    fun startOutcomes() {
        val perPart = PrepareTestFixtures.loadPrepare("per-part")
        val resumedStart = PrepareTestFixtures.loadStart(200, "resumed")
        val startingStart = PrepareTestFixtures.loadStart(202, "starting")
        val abortedStart = PrepareTestFixtures.loadStart(200, "aborted")
        val attemptId = startingStart.attemptId

        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(perPart))
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$attemptId",
            200,
            PrepareTestFixtures.readAttemptStateRaw(200, "starting"),
        )
        server.respondSequence(
            "POST",
            "/api/v1/me/attempts",
            { ContractFakeServer.jsonResponse(200, PrepareTestFixtures.encodeStart(resumedStart)) },
            { ContractFakeServer.jsonResponse(202, PrepareTestFixtures.encodeStart(startingStart)) },
            { ContractFakeServer.jsonResponse(200, PrepareTestFixtures.encodeStart(abortedStart)) },
        )

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        val startContextStore = StartContextStore(storeProvider)
        lateinit var navigator: AppNavigator

        composeRule.setContent {
            val navController = rememberNavController()
            navigator = AppNavigator(navController)
            CompositionLocalProvider(
                LocalFormulaEngine provides SourceBoxFormulaEngine(),
                LocalPrepareStartContextStoreOverride provides startContextStore,
            ) {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    navController = navController,
                    navigator = navigator,
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                )
            }
        }

        composeRule.runOnIdle {
            navigator.switchTab(Destination.MyExams, reload = false)
            navigator.openPrepare(entitlementId.toString())
        }
        waitForStartButton()
        clickStartButton()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            navigator.navController.currentDestination?.route == AppRoute.ATTEMPT_PATTERN
        }
        assertEquals(
            resumedStart.attemptId.toString(),
            navigator.navController.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID),
        )
        assertNotNull(startContextStore.read(resumedStart.attemptId))
        composeRule.runOnIdle { navigator.closeAttempt() }
        composeRule.waitForIdle()
        assertEquals(
            AppRoute.rootRouteFor(Destination.MyExams),
            navigator.navController.currentDestination?.route,
        )

        composeRule.runOnIdle { navigator.openPrepare(entitlementId.toString()) }
        waitForStartButton()
        clickStartButton()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.WAITING_POLLING_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        val saved = startContextStore.read(attemptId)
        assertNotNull(saved)
        assertEquals(entitlementId.toString(), saved!!.entitlementId)
        assertEquals(perPart.examName, saved.examTitle)

        composeRule.onNodeWithTag(PrepareTestTags.WAITING_BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        assertEquals(
            AppRoute.rootRouteFor(Destination.MyExams),
            navigator.navController.currentDestination?.route,
        )

        composeRule.runOnIdle { navigator.openPrepare(entitlementId.toString()) }
        waitForStartButton()
        clickStartButton()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertNotNull(startContextStore.read(abortedStart.attemptId))
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_REASON)
            .assertTextContains("Chưa tạo được bộ câu hỏi cho đề này.")
    }

    @Test
    fun reenterStarting() {
        val openStarting = PrepareTestFixtures.loadPrepare("open-starting")
        val startingAttemptId = openStarting.openAttempt!!.attemptId!!
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(openStarting))
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$startingAttemptId",
            200,
            PrepareTestFixtures.readAttemptStateRaw(200, "starting"),
        )

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        lateinit var navigator: AppNavigator

        composeRule.setContent {
            val navController = rememberNavController()
            navigator = AppNavigator(navController)
            CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    navController = navController,
                    navigator = navigator,
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                )
            }
        }

        composeRule.runOnIdle {
            navigator.switchTab(Destination.MyExams, reload = false)
            navigator.openPrepare(entitlementId.toString())
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.WAITING_SCREEN).assertIsDisplayed()
                composeRule.onNodeWithTag(PrepareTestTags.WAITING_POLLING_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(0, server.requestCount("POST", "/api/v1/me/attempts"))

        composeRule.onNodeWithTag(PrepareTestTags.WAITING_BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        assertEquals(
            AppRoute.rootRouteFor(Destination.MyExams),
            navigator.navController.currentDestination?.route,
        )

        val pollCountAfterBack = server.requestCount("GET", "/api/v1/me/attempts/$startingAttemptId")
        composeRule.mainClock.advanceTimeBy(2_500L)
        composeRule.waitForIdle()
        assertEquals(
            pollCountAfterBack,
            server.requestCount("GET", "/api/v1/me/attempts/$startingAttemptId"),
        )

        composeRule.runOnIdle { navigator.openPrepare(entitlementId.toString()) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.WAITING_SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(0, server.requestCount("POST", "/api/v1/me/attempts"))
        assertTrue(server.requestCount("GET", preparePath) >= 2)
    }

    private fun waitForStartButton() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.LIST)
                    .performScrollToNode(hasTestTag(PrepareTestTags.START_BUTTON))
                composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun clickStartButton() {
        composeRule.onNodeWithTag(PrepareTestTags.LIST)
            .performScrollToNode(hasTestTag(PrepareTestTags.START_BUTTON))
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).performClick()
    }
}
