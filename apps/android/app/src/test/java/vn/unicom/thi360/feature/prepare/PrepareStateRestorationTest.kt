package vn.unicom.thi360.feature.prepare

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class PrepareStateRestorationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer

    private val entitlementId = UUID.fromString("00000000-0000-7000-8000-000000000001")
    private val preparePath = "/api/v1/me/entitlements/$entitlementId/prepare"

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = ContractFakeServer().start()
        app.setTestOverrides(connectivityMonitor = MutableConnectivityMonitor(initialOnline = true))
    }

    @After
    fun tearDown() {
        PrepareSessionMemory.clearAllForTesting()
        AppSessionContainer.setTestStack(app, null)
        app.setTestOverrides()
        server.shutdown()
    }

    private fun createActiveStack() = Thi360ApiFactory.createSessionStack(
        baseUrl = server.url("/"),
        environment = Environment.DEV,
        tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "restore-access",
                refreshToken = "restore-refresh",
                accessExpiresIn = 3600,
                refreshExpiresIn = 86400,
                mustChangePassword = false,
                accountId = "acc-restore",
                username = "hv01",
            ),
        ),
        localeProvider = { AppLocale.VI },
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun rotationFoldAndResizePreserveSelectionWithoutDuplicateApiCalls() {
        val response = PrepareTestFixtures.prepareWithSubjectChoice(entitlementId = entitlementId)
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(response))

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        var currentNavigator: AppNavigator? = null

        fun mountAppRoot() {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    val navController = rememberNavController()
                    val navigator = remember(navController) { AppNavigator(navController) }
                    currentNavigator = navigator
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
            }
        }

        mountAppRoot()

        composeRule.runOnIdle {
            val nav = checkNotNull(currentNavigator)
            nav.switchTab(Destination.MyExams, reload = false)
            nav.openPrepare(entitlementId.toString())
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("SCIENCE")).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", preparePath))

        composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("SCIENCE")).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.LIST)
            .performScrollToNode(hasTestTag(PrepareTestTags.subjectCheckbox("LY")))
        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("LY")).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("HOA")).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.SUBJECT_PROGRESS).assertTextContains("2/3", substring = true)

        for (qualifiers in listOf("w840dp-h720dp", "w1280dp-h800dp", "w360dp-h640dp", "w412dp-h915dp")) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            composeRule.activityRule.scenario.recreate()
            mountAppRoot()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                ShadowLooper.idleMainLooper()
                runCatching {
                    composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("SCIENCE")).assertIsSelected()
                    composeRule.onNodeWithTag(PrepareTestTags.SUBJECT_PROGRESS)
                        .assertTextContains("2/3", substring = true)
                    true
                }.getOrDefault(false)
            }
            assertEquals(1, server.requestCount("GET", preparePath))
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun rotationDuringInFlightStartAndWaitingPreservesStateWithoutDuplicateCalls() {
        val perPart = PrepareTestFixtures.loadPrepare("per-part")
        val startingStart = PrepareTestFixtures.loadStart(202, "starting")
        val attemptId = startingStart.attemptId
        val attemptPath = "/api/v1/me/attempts/$attemptId"

        val startEntered = CountDownLatch(1)
        val allowStartFinish = CountDownLatch(1)
        val pollCounter = AtomicInteger(0)

        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(perPart))
        server.respond("POST", "/api/v1/me/attempts") {
            startEntered.countDown()
            allowStartFinish.await(5, TimeUnit.SECONDS)
            ContractFakeServer.jsonResponse(202, PrepareTestFixtures.encodeStart(startingStart))
        }
        server.respond("GET", attemptPath) {
            val count = pollCounter.incrementAndGet()
            if (count == 1) {
                ContractFakeServer.jsonResponse(200, PrepareTestFixtures.readAttemptStateRaw(200, "starting"))
            } else {
                ContractFakeServer.jsonResponse(200, PrepareTestFixtures.readAttemptStateRaw(200, "aborted"))
            }
        }

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        var currentNavigator: AppNavigator? = null

        fun mountAppRoot() {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    val navController = rememberNavController()
                    val navigator = remember(navController) { AppNavigator(navController) }
                    currentNavigator = navigator
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
            }
        }

        mountAppRoot()

        composeRule.runOnIdle {
            val nav = checkNotNull(currentNavigator)
            nav.switchTab(Destination.MyExams, reload = false)
            nav.openPrepare(entitlementId.toString())
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.LIST)
                    .performScrollToNode(hasTestTag(PrepareTestTags.START_BUTTON))
                composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", preparePath))

        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            startEntered.count == 0L
        }

        RuntimeEnvironment.setQualifiers("w840dp-h720dp")
        composeRule.activityRule.scenario.recreate()
        mountAppRoot()
        composeRule.waitForIdle()
        allowStartFinish.countDown()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.WAITING_POLLING_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", preparePath))
        assertEquals(1, server.requestCount("POST", "/api/v1/me/attempts"))

        val pollsBeforePollingRotate = server.requestCount("GET", attemptPath)
        RuntimeEnvironment.setQualifiers("w412dp-h915dp")
        composeRule.activityRule.scenario.recreate()
        mountAppRoot()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.WAITING_POLLING_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", preparePath))
        assertEquals(1, server.requestCount("POST", "/api/v1/me/attempts"))
        assertEquals(pollsBeforePollingRotate, server.requestCount("GET", attemptPath))

        composeRule.waitUntil(timeoutMillis = 6_000) {
            ShadowLooper.idleMainLooper(1_100, TimeUnit.MILLISECONDS)
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_REASON)
            .assertTextContains("Việc chuẩn bị đề bị gián đoạn.")

        RuntimeEnvironment.setQualifiers("w1280dp-h800dp")
        composeRule.activityRule.scenario.recreate()
        mountAppRoot()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_REASON)
            .assertTextContains("Việc chuẩn bị đề bị gián đoạn.")
        assertEquals(1, server.requestCount("GET", preparePath))
        assertEquals(1, server.requestCount("POST", "/api/v1/me/attempts"))
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun offScreenPrepareUnderCheckoutClearsViewModelWhenTabBackStackCleared() {
        val exhausted = PrepareTestFixtures.loadPrepare("exhausted-purchase")
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(exhausted))

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        var currentNavigator: AppNavigator? = null

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val navController = rememberNavController()
                val navigator = remember(navController) { AppNavigator(navController) }
                currentNavigator = navigator
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
        }

        composeRule.runOnIdle {
            val nav = checkNotNull(currentNavigator)
            nav.switchTab(Destination.MyExams, reload = false)
            nav.openPrepare(entitlementId.toString())
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.LIST)
                    .performScrollToNode(hasTestTag(PrepareTestTags.BUY_MORE_BUTTON))
                composeRule.onNodeWithTag(PrepareTestTags.BUY_MORE_BUTTON).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, PrepareSessionMemory.activeEntryCountForTesting())

        composeRule.onNodeWithTag(PrepareTestTags.BUY_MORE_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            checkNotNull(currentNavigator).navController.currentDestination?.route?.contains("checkout/") == true
        }
        assertEquals(1, PrepareSessionMemory.activeEntryCountForTesting())

        composeRule.runOnIdle {
            checkNotNull(currentNavigator).switchTab(Destination.MyExams, reload = true)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            PrepareSessionMemory.activeEntryCountForTesting() == 0
        }
        assertTrue(PrepareSessionMemory.activeEntryCountForTesting() == 0)
    }
}
