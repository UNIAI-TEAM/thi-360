package vn.unicom.thi360.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
import vn.unicom.thi360.core.network.generated.model.AttemptHistoryItem
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.myexams.MyExamsSessionMemory
import vn.unicom.thi360.feature.myexams.MyExamsTestFixtures
import vn.unicom.thi360.feature.myexams.MyExamsTestTags
import vn.unicom.thi360.feature.myexams.NavigationClickGuard
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class HistoryNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = ContractFakeServer().start()
        app.setTestOverrides(connectivityMonitor = MutableConnectivityMonitor(initialOnline = true))
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=EXAM", fixtureNameFilter = "exam")
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=SUBJECT", fixtureNameFilter = "subject")
    }

    @After
    fun tearDown() {
        MyExamsSessionMemory.clearAllForTesting()
        AppSessionContainer.setTestStack(app, null)
        app.setTestOverrides()
        server.shutdown()
    }

    @Test
    fun historyButtonResetsFilter() {
        val examCard = MyExamsTestFixtures.makeCard(
            code = "EX-HIST",
            name = "Bài có lịch sử",
            state = MyExamCard.State.EXHAUSTED,
            attemptsLeft = 0,
            actions = listOf(MyExamCard.Actions.HISTORY),
        )
        server.respondJson("GET", "/api/v1/me/exams", 200, MyExamsTestFixtures.encodeResponse(listOf(examCard)))
        server.respondIndexed("listHistory", 200, fixtureNameFilter = "attempts-list-200.json")

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("access", "refresh", 300, 604800, false, "hv01", "acc-1"),
            ),
            localeProvider = { AppLocale.VI },
        )
        AppSessionContainer.setTestStack(app, stack)
        val memory = MyExamsSessionMemory.forSession(stack, app.connectivityMonitor)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/me/attempts") == 1
        }
        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performTextInput("hsa")
        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performImeAction()
        composeRule.onNodeWithTag(HistoryTestTags.SCOPE_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.scopeOption(HistoryScope.PART)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            memory.historyFeed.state.value.filter.scope == HistoryScope.PART &&
                memory.historyFeed.state.value.filter.trimmedQuery == "hsa"
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.examCardHistory("EX-HIST")).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        val historyCallsBefore = server.requestCount("GET", "/api/v1/me/attempts")
        composeRule.onNodeWithTag(MyExamsTestTags.examCardHistory("EX-HIST")).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/me/attempts") > historyCallsBefore &&
                memory.historyFeed.state.value.filter.isEmpty
        }

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History)).assertIsDisplayed()
        composeRule.onNodeWithTag(HistoryTestTags.CLEAR_FILTERS_BUTTON).assertDoesNotExist()
        assertEquals("", memory.historyFeed.state.value.searchInput)

        val resetUrl = server.lastRequest("GET", "/api/v1/me/attempts")!!.requestUrl!!
        assertEquals("0", resetUrl.queryParameter("page"))
        assertEquals("20", resetUrl.queryParameter("size"))
        assertNull(resetUrl.queryParameter("q"))
        assertNull(resetUrl.queryParameter("kind"))
        assertNull(resetUrl.queryParameter("scope"))
        assertNull(resetUrl.queryParameter("examId"))
        assertNull(resetUrl.queryParameter("from"))
        assertNull(resetUrl.queryParameter("to"))
    }

    @Test
    fun rowActions() {
        val inProgress = HistoryTestFixtures.makeAttempt(
            idSeed = "act-inp",
            examName = "Đề đang làm",
            state = AttemptHistoryItem.State.IN_PROGRESS,
            action = AttemptHistoryItem.Action.CONTINUE,
            displayScore = null,
            rawScore = null,
        )
        val scored = HistoryTestFixtures.makeAttempt(
            idSeed = "act-scored",
            examName = "Đề đã chấm",
            state = AttemptHistoryItem.State.SCORED,
            action = AttemptHistoryItem.Action.VIEW_RESULT,
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts",
            200,
            HistoryTestFixtures.encodePage(listOf(inProgress, scored)),
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

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        val inpId = inProgress.attemptId.toString()
        val scoredId = scored.attemptId.toString()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(HistoryTestTags.rowContinueButton(inpId)).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        val nav = checkNotNull(capturedNavController)
        val historyRoute = nav.currentDestination?.route

        composeRule.onNodeWithTag(HistoryTestTags.rowTitle(inpId)).performClick()
        composeRule.waitForIdle()
        assertEquals(historyRoute, nav.currentDestination?.route)

        composeRule.onNodeWithTag(HistoryTestTags.rowContinueButton(inpId)).performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoute.ATTEMPT_PATTERN, nav.currentDestination?.route)
        assertEquals(inpId, nav.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID))

        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HistoryTestTags.rowTitle(scoredId)).performClick()
        composeRule.waitForIdle()
        assertEquals(scoredId, nav.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID))
    }

    @Test
    fun rapidDoubleTapOnRowActionOnlyDispatchesOnce() {
        val scored = HistoryTestFixtures.makeAttempt(
            idSeed = "dbl-row",
            state = AttemptHistoryItem.State.SCORED,
            action = AttemptHistoryItem.Action.VIEW_RESULT,
        )
        var now = 1_000L
        val guard = NavigationClickGuard(minIntervalMillis = 500L, clockMillis = { now })
        val opened = mutableListOf<String>()

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                HistoryScreen(
                    feedState = HistoryFeedState(
                        loadState = LoadState.Loaded(HistoryPage(listOf(scored), 0, 1, 1, false)),
                    ),
                    showCompactBrandHeader = false,
                    onSearchInputChanged = {},
                    onSearchSubmit = {},
                    onSelectKind = {},
                    onSelectScope = {},
                    onSelectFromDate = {},
                    onSelectToDate = {},
                    onClearAllFilters = {},
                    onLoadNextPage = {},
                    onRetry = {},
                    onGoToMyExams = {},
                    onContinueAttempt = {},
                    onOpenResult = { opened.add(it) },
                    clickGuard = guard,
                )
            }
        }

        val id = scored.attemptId.toString()
        composeRule.onNodeWithTag(HistoryTestTags.rowViewResultButton(id)).performClick()
        now = 1_100L
        composeRule.onNodeWithTag(HistoryTestTags.rowViewResultButton(id)).performClick()
        assertEquals(listOf(id), opened)
    }
}
