package vn.unicom.thi360.feature.myexams

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
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
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class MyExamsNavigationTest {

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
    fun allCardActionsOpenExpectedRoutesWithoutExtraApiCalls() {
        val activeId = UUID.fromString("00000000-0000-7000-8000-000000000501")
        val resultId = UUID.fromString("00000000-0000-7000-8000-000000000502")
        val startEntId = UUID.fromString("00000000-0000-7000-8000-000000000503")

        val inProgressCard = MyExamsTestFixtures.makeCard(
            code = "EX-INP",
            name = "Đề đang làm",
            state = MyExamCard.State.IN_PROGRESS,
            activeAttemptId = activeId,
            resultAttemptId = resultId,
            actions = listOf(
                MyExamCard.Actions.CONTINUE,
                MyExamCard.Actions.VIEW_RESULT,
                MyExamCard.Actions.HISTORY,
                MyExamCard.Actions.BUY_MORE,
            ),
        )
        val canStartCard = MyExamsTestFixtures.makeCard(
            code = "EX-START",
            name = "Đề bắt đầu",
            entitlementId = startEntId,
            state = MyExamCard.State.CAN_START,
            actions = listOf(MyExamCard.Actions.START, MyExamCard.Actions.REBUY),
        )

        server.respondJson(
            "GET",
            "/api/v1/me/exams",
            200,
            MyExamsTestFixtures.encodeResponse(listOf(inProgressCard, canStartCard)),
        )

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("access", "refresh", 300, 604800, false, "hv01", "acc-1"),
            ),
            localeProvider = { AppLocale.VI },
        )
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
                composeRule.onNodeWithTag(MyExamsTestTags.examCardContinue("EX-INP")).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(MyExamsTestTags.examCardStart("EX-INP")).assertDoesNotExist()
        val continueRect = composeRule.onNodeWithTag(MyExamsTestTags.examCardContinue("EX-INP"))
            .getUnclippedBoundsInRoot()
        val viewResultRect = composeRule.onNodeWithTag(MyExamsTestTags.examCardViewResult("EX-INP"))
            .getUnclippedBoundsInRoot()
        val historyRect = composeRule.onNodeWithTag(MyExamsTestTags.examCardHistory("EX-INP"))
            .getUnclippedBoundsInRoot()
        val buyMoreRect = composeRule.onNodeWithTag(MyExamsTestTags.examCardBuyMore("EX-INP"))
            .getUnclippedBoundsInRoot()
        assertTrue(continueRect.bottom <= viewResultRect.top)
        assertTrue(kotlin.math.abs((viewResultRect.top - historyRect.top).value) < 4f)
        assertTrue(viewResultRect.bottom <= buyMoreRect.top)

        val nav = checkNotNull(capturedNavController)
        val initialExamsCalls = server.requestCount("GET", "/api/v1/me/exams")

        composeRule.onNodeWithTag(MyExamsTestTags.examCardContinue("EX-INP")).performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoute.ATTEMPT_PATTERN, nav.currentDestination?.route)
        assertEquals(activeId.toString(), nav.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID))
        assertEquals(initialExamsCalls, server.requestCount("GET", "/api/v1/me/exams"))

        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/me/exams") >= initialExamsCalls + 1
        }

        composeRule.onNodeWithTag(MyExamsTestTags.examCardViewResult("EX-INP")).performClick()
        composeRule.waitForIdle()
        assertEquals(
            resultId.toString(),
            nav.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID),
        )
        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MyExamsTestTags.examCardBuyMore("EX-INP")).performClick()
        composeRule.waitForIdle()
        assertEquals("EX-INP", nav.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_CODE))
        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.examCardStart("EX-START")))
        composeRule.onNodeWithTag(MyExamsTestTags.examCardStart("EX-START")).performClick()
        composeRule.waitForIdle()
        assertEquals(
            startEntId.toString(),
            nav.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ENTITLEMENT_ID),
        )
        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.examCardRebuy("EX-START")))
        composeRule.onNodeWithTag(MyExamsTestTags.examCardRebuy("EX-START")).performClick()
        composeRule.waitForIdle()
        assertEquals("EX-START", nav.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_CODE))
    }

    @Test
    fun rapidDoubleTapOnlyDispatchesOnce() {
        val activeId = UUID.randomUUID()
        val card = MyExamsTestFixtures.makeCard(
            code = "DOUBLE-TAP",
            state = MyExamCard.State.IN_PROGRESS,
            activeAttemptId = activeId,
            actions = listOf(MyExamCard.Actions.CONTINUE),
        )
        var nowMillis = 1_000L
        val guard = NavigationClickGuard(minIntervalMillis = 500L, clockMillis = { nowMillis })
        val opens = mutableListOf<String>()

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                MyExamsScreen(
                    feedState = MyExamsFeedState(loadState = LoadState.Loaded(listOf(card))),
                    greetingName = "An",
                    supportContact = SupportContact(),
                    showCompactBrandHeader = false,
                    onPullToRefresh = {},
                    onRetry = {},
                    onExploreExams = {},
                    onOpenOrder = {},
                    onOpenProduct = {},
                    onContinueAttempt = { opens.add(it) },
                    onStartPrepare = {},
                    onOpenResult = {},
                    onOpenHistoryReset = {},
                    onOpenCheckout = {},
                    clickGuard = guard,
                )
            }
        }

        composeRule.onNodeWithTag(MyExamsTestTags.examCardContinue("DOUBLE-TAP")).performClick()
        nowMillis = 1_100L
        composeRule.onNodeWithTag(MyExamsTestTags.examCardContinue("DOUBLE-TAP")).performClick()

        assertEquals(listOf(activeId.toString()), opens)
    }
}
