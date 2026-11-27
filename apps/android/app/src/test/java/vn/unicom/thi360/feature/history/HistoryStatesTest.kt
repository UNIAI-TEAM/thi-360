package vn.unicom.thi360.feature.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.util.TimeZone
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
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.myexams.MyExamsSessionMemory
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.routes.LocalMyExamsClockOverride
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class HistoryStatesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer
    private lateinit var connectivity: MutableConnectivityMonitor
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        app = ApplicationProvider.getApplicationContext()
        server = ContractFakeServer().start()
        connectivity = MutableConnectivityMonitor(initialOnline = true)
        app.setTestOverrides(connectivityMonitor = connectivity)
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=EXAM", fixtureNameFilter = "exam")
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=SUBJECT", fixtureNameFilter = "subject")
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
        MyExamsSessionMemory.clearAllForTesting()
        AppSessionContainer.setTestStack(app, null)
        app.setTestOverrides()
        server.shutdown()
    }

    @Test
    fun loadingErrorNoAutoRetryAndRetryFlow() {
        val gate = CountDownLatch(1)
        val arrived = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val item = HistoryTestFixtures.makeAttempt(idSeed = "h-ok", examName = "Đề sau khi thử lại")

        server.respondDynamic("GET", "/api/v1/me/attempts") {
            val n = calls.incrementAndGet()
            if (n == 1) {
                arrived.countDown()
                gate.await(5, TimeUnit.SECONDS)
                ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR"}""")
            } else {
                ContractFakeServer.jsonResponse(200, HistoryTestFixtures.encodePage(listOf(item)))
            }
        }

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)

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
        composeRule.waitUntil(timeoutMillis = 5_000) { arrived.count == 0L }
        composeRule.onNodeWithTag(HistoryTestTags.LOADING_SKELETON)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Đang tải lịch sử luyện thi")
        composeRule.onNodeWithTag(HistoryTestTags.FILTER_SECTION).assertIsDisplayed()

        gate.countDown()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(HistoryTestTags.ERROR_STATE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts"))
        composeRule.onNodeWithText("Không tải được lịch sử luyện thi").assertIsDisplayed()
        composeRule.onNodeWithText("Các lượt thi của bạn vẫn được giữ. Hãy thử lại sau ít phút.")
            .assertIsDisplayed()

        composeRule.onNodeWithTag(HistoryTestTags.ERROR_RETRY_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Đề sau khi thử lại").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(2, server.requestCount("GET", "/api/v1/me/attempts"))
    }

    @Test
    fun staleBannerFilterDimmingAndOfflineKeepFiltersVisible() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val loadedAt = Instant.parse("2026-11-20T02:05:00Z").toEpochMilli()
        val item1 = HistoryTestFixtures.makeAttempt(idSeed = "h-stale-1", examName = "Đề cũ")
        val item2 = HistoryTestFixtures.makeAttempt(idSeed = "h-stale-2", examName = "Đề lọc mới")
        val filterGate = CountDownLatch(1)
        val filterArrived = CountDownLatch(1)
        val calls = AtomicInteger(0)

        server.respondDynamic("GET", "/api/v1/me/attempts") {
            when (calls.incrementAndGet()) {
                1 -> ContractFakeServer.jsonResponse(200, HistoryTestFixtures.encodePage(listOf(item1)))
                2 -> {
                    filterArrived.countDown()
                    filterGate.await(5, TimeUnit.SECONDS)
                    ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR"}""")
                }
                else -> ContractFakeServer.jsonResponse(200, HistoryTestFixtures.encodePage(listOf(item2)))
            }
        }

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)

        composeRule.setContent {
            CompositionLocalProvider(LocalMyExamsClockOverride provides { loadedAt }) {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                )
            }
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Đề cũ").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(HistoryTestTags.SCOPE_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.scopeOption(HistoryScope.PART)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { filterArrived.count == 0L }
        composeRule.onNodeWithTag(HistoryTestTags.FILTER_REFRESHING_DIMMED).assertIsDisplayed()
        composeRule.onNodeWithText("Đề cũ").assertIsDisplayed()

        filterGate.countDown()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(HistoryTestTags.STALE_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Dữ liệu chưa được cập nhật.").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Không tải lại được dữ liệu; đang hiện bản đã tải lúc 09:05, 20/11/2026.",
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(HistoryTestTags.STALE_RETRY_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Đề lọc mới").assertIsDisplayed()
                composeRule.onNodeWithTag(HistoryTestTags.STALE_BANNER).assertDoesNotExist()
                true
            }.getOrDefault(false)
        }
        assertEquals("PART", server.lastRequest("GET", "/api/v1/me/attempts")?.requestUrl?.queryParameter("scope"))

        connectivity.setOnline(false)
        composeRule.onNodeWithTag(HistoryTestTags.SCOPE_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.scopeOption(HistoryScope.SUBJECT)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(HistoryTestTags.OFFLINE_STATE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(HistoryTestTags.FILTER_SECTION).assertIsDisplayed()
        composeRule.onNodeWithText("Không có kết nối").assertIsDisplayed()

        connectivity.setOnline(true)
        composeRule.onNodeWithTag(HistoryTestTags.OFFLINE_RETRY_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Đề lọc mới").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun emptyNoAttemptsAndEmptyNoMatchStates() {
        server.respondDynamic("GET", "/api/v1/me/attempts") { req ->
            val scope = req.requestUrl?.queryParameter("scope")
            if (scope != null) {
                ContractFakeServer.jsonResponse(200, HistoryTestFixtures.encodePage(emptyList(), totalItems = 0, totalPages = 0))
            } else {
                ContractFakeServer.jsonResponse(200, HistoryTestFixtures.encodePage(emptyList(), totalItems = 0, totalPages = 0))
            }
        }
        server.respondIndexed("listMyExams", 200, fixtureNameFilter = "empty")

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)

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
            runCatching {
                composeRule.onNodeWithTag(HistoryTestTags.EMPTY_NO_ATTEMPTS).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Bạn chưa có lượt luyện thi nào").assertIsDisplayed()

        composeRule.onNodeWithTag(HistoryTestTags.SCOPE_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.scopeOption(HistoryScope.PART)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(HistoryTestTags.EMPTY_NO_MATCH).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Chưa có lượt thi phù hợp").assertIsDisplayed()
        composeRule.onNodeWithTag(HistoryTestTags.EMPTY_CLEAR_FILTERS_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(HistoryTestTags.EMPTY_NO_ATTEMPTS).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(HistoryTestTags.EMPTY_TO_MY_EXAMS_BUTTON).performClick()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.MyExams)).assertIsDisplayed()
    }

    @Test
    fun staleWithoutTimeShowsFallbackDescription() {
        val item = HistoryTestFixtures.makeAttempt(idSeed = "notime")
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                HistoryScreen(
                    feedState = HistoryFeedState(
                        loadState = LoadState.Loaded(
                            value = HistoryPage(listOf(item), 0, 1, 1, false),
                            stale = true,
                        ),
                        lastLoadedAtMillis = null,
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
                    onOpenResult = {},
                )
            }
        }
        composeRule.onNodeWithText(
            "Không tải lại được dữ liệu; đang hiện bản đã tải trước đó.",
        ).assertIsDisplayed()
    }

    @Test
    fun foregroundResumeDoesNotAutoReloadOrResetPagination() {
        val allItems = (1..45).map { idx ->
            HistoryTestFixtures.makeAttempt(idSeed = "fg-$idx", examName = "Lượt nền $idx")
        }
        server.respondDynamic("GET", "/api/v1/me/attempts") { req ->
            val page = req.requestUrl?.queryParameter("page")?.toInt() ?: 0
            val start = page * 20
            val end = minOf(start + 20, allItems.size)
            val slice = if (start < allItems.size) allItems.subList(start, end) else emptyList()
            ContractFakeServer.jsonResponse(
                200,
                HistoryTestFixtures.encodePage(slice, page = page, size = 20, totalItems = 45, totalPages = 3),
            )
        }

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        val memory = MyExamsSessionMemory.forSession(stack, app.connectivityMonitor)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
            override val lifecycle: Lifecycle get() = registry
        }
        var capturedNavController: NavHostController? = null

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
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
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            (memory.historyFeed.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 20
        }

        composeRule.onNodeWithTag(HistoryTestTags.LIST).performScrollToIndex(21)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            (memory.historyFeed.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 40
        }
        assertEquals(2, server.requestCount("GET", "/api/v1/me/attempts"))

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
        assertEquals(2, server.requestCount("GET", "/api/v1/me/attempts"))
        assertEquals(40, (memory.historyFeed.state.value.loadState as LoadState.Loaded).value.items.size)
    }

    private fun createActiveStack() = Thi360ApiFactory.createSessionStack(
        baseUrl = server.url("/"),
        environment = Environment.DEV,
        tokenStore = InMemoryTokenStore(
            Tokens("access", "refresh", 300, 604800, false, "hv01", "acc-1"),
        ),
        localeProvider = { AppLocale.VI },
    )
}
