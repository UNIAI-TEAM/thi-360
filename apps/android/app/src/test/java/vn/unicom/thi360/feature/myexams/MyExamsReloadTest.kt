package vn.unicom.thi360.feature.myexams

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
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
import org.robolectric.annotation.Config
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class MyExamsReloadTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = ContractFakeServer().start()
        app.setTestOverrides(connectivityMonitor = MutableConnectivityMonitor(initialOnline = true))
    }

    @After
    fun tearDown() {
        MyExamsSessionMemory.clearAllForTesting()
        AppSessionContainer.setTestStack(app, null)
        app.setTestOverrides()
        server.shutdown()
    }

    @Test
    fun returningFromTabOrSubRouteReloadsAndKeepsPreviousListWithoutSkeleton() {
        val activeAttempt = UUID.fromString("00000000-0000-7000-8000-000000000901")
        val initialCards = listOf(
            MyExamsTestFixtures.makeCard(
                code = "EX-RELOAD",
                name = "Bài thi phiên bản 1",
                state = MyExamCard.State.IN_PROGRESS,
                activeAttemptId = activeAttempt,
                actions = listOf(MyExamCard.Actions.CONTINUE),
            ),
        )
        val refreshedCards = listOf(
            MyExamsTestFixtures.makeCard(
                code = "EX-RELOAD",
                name = "Bài thi phiên bản 2",
                state = MyExamCard.State.CAN_START,
                actions = listOf(MyExamCard.Actions.START),
            ),
        )
        val secondCallGate = CountDownLatch(1)
        val secondCallArrived = CountDownLatch(1)
        val callCounter = AtomicInteger(0)

        server.respondDynamic("GET", "/api/v1/me/exams") {
            val n = callCounter.incrementAndGet()
            if (n == 1) {
                ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(initialCards))
            } else {
                secondCallArrived.countDown()
                secondCallGate.await(5, TimeUnit.SECONDS)
                ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(refreshedCards))
            }
        }

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        var capturedNavController: NavHostController? = null

        composeRule.setContent {
            val navController = rememberNavController()
            capturedNavController = navController
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                navController = navController,
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Bài thi phiên bản 1").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(MyExamsTestTags.examCardContinue("EX-RELOAD")).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            checkNotNull(capturedNavController).popBackStack()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { secondCallArrived.count == 0L }
        composeRule.onNodeWithText("Bài thi phiên bản 1").assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.LOADING_SKELETON).assertDoesNotExist()

        secondCallGate.countDown()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Bài thi phiên bản 2").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(2, server.requestCount("GET", "/api/v1/me/exams"))
    }

    @Test
    fun coldRestartClearsInMemoryDataAndShowsLoadingSkeleton() {
        val run1Cards = listOf(MyExamsTestFixtures.makeCard(code = "COLD-1", name = "Bài lần chạy 1"))
        val run2Cards = listOf(MyExamsTestFixtures.makeCard(code = "COLD-2", name = "Bài lần chạy 2"))
        val secondRunGate = CountDownLatch(1)
        val secondRunArrived = CountDownLatch(1)
        val callCounter = AtomicInteger(0)

        server.respondDynamic("GET", "/api/v1/me/exams") {
            val n = callCounter.incrementAndGet()
            if (n == 1) {
                ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(run1Cards))
            } else {
                secondRunArrived.countDown()
                secondRunGate.await(5, TimeUnit.SECONDS)
                ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(run2Cards))
            }
        }

        var stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        var launchGeneration by mutableIntStateOf(1)

        composeRule.setContent {
            key(launchGeneration) {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                )
            }
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Bài lần chạy 1").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        launchGeneration = 2
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { secondRunArrived.count == 0L }
        composeRule.onNodeWithTag(MyExamsTestTags.LOADING_SKELETON).assertIsDisplayed()
        composeRule.onNodeWithText("Bài lần chạy 1").assertDoesNotExist()

        secondRunGate.countDown()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Bài lần chạy 2").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun foregroundResumeDoesNotAutoReloadButExplicitSwitchTabReloadDoes() {
        val cards = listOf(MyExamsTestFixtures.makeCard(code = "BG-1", name = "Bài nền A26"))
        server.respondJson("GET", "/api/v1/me/exams", 200, MyExamsTestFixtures.encodeResponse(cards))

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
            override val lifecycle: Lifecycle get() = registry
        }
        var capturedNavController: NavHostController? = null
        var capturedNavigator: AppNavigator? = null

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val navController = rememberNavController()
                val navigator = remember(navController) { AppNavigator(navController) }
                capturedNavController = navController
                capturedNavigator = navigator
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

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Bài nền A26").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", "/api/v1/me/exams"))

        composeRule.runOnIdle {
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            assertEquals(
                Lifecycle.State.CREATED,
                checkNotNull(capturedNavController).currentBackStackEntry?.lifecycle?.currentState,
            )
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            assertEquals(
                Lifecycle.State.RESUMED,
                checkNotNull(capturedNavController).currentBackStackEntry?.lifecycle?.currentState,
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, server.requestCount("GET", "/api/v1/me/exams"))

        composeRule.runOnIdle {
            checkNotNull(capturedNavigator).switchTab(Destination.MyExams, reload = true)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/me/exams") == 2
        }
    }

    private fun createActiveStack() = Thi360ApiFactory.createSessionStack(
        baseUrl = server.url("/"),
        environment = Environment.DEV,
        tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "active-access",
                refreshToken = "active-refresh",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
                accountId = "acc-demo",
            ),
        ),
        localeProvider = { AppLocale.VI },
    )
}
