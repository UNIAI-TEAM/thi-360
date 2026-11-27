package vn.unicom.thi360.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.catalog.CatalogOptionsCache
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.model.PageResponseAttemptHistoryItem
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.myexams.MyExamsSessionMemory
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class HistoryPagingTest {

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
    fun infiniteScrollLoads45ItemsAcrossThreePagesShowsBottomIndicatorAndStopsAtEnd() {
        val allItems = (1..45).map { idx ->
            HistoryTestFixtures.makeAttempt(idSeed = "p-$idx", examName = "Đề thứ $idx")
        }
        val page1Gate = CountDownLatch(1)
        val page1Arrived = CountDownLatch(1)

        server.respondDynamic("GET", "/api/v1/me/attempts") { req ->
            val page = req.requestUrl?.queryParameter("page")?.toInt() ?: 0
            if (page == 1) {
                page1Arrived.countDown()
                page1Gate.await(5, TimeUnit.SECONDS)
            }
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
            (memory.historyFeed.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 20
        }

        composeRule.onNodeWithTag(HistoryTestTags.LIST).performScrollToIndex(21)
        composeRule.waitUntil(timeoutMillis = 5_000) { page1Arrived.count == 0L }
        composeRule.onNodeWithTag(HistoryTestTags.LIST)
            .performScrollToNode(hasTestTag(HistoryTestTags.LOAD_MORE_INDICATOR))
        composeRule.onNodeWithTag(HistoryTestTags.LOAD_MORE_INDICATOR).assertIsDisplayed()

        page1Gate.countDown()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            (memory.historyFeed.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 40
        }

        composeRule.onNodeWithTag(HistoryTestTags.LIST).performScrollToIndex(41)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            (memory.historyFeed.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 45
        }

        composeRule.onNodeWithTag(HistoryTestTags.LIST).performScrollToIndex(46)
        composeRule.waitForIdle()
        val pagesRequested = server.requests("GET", "/api/v1/me/attempts")
            .mapNotNull { it.requestUrl?.queryParameter("page")?.toInt() }
        assertEquals(listOf(0, 1, 2), pagesRequested)
    }

    @Test
    fun exactTwentyItemsOnlyCallsPageZero() {
        val items = (1..20).map { idx -> HistoryTestFixtures.makeAttempt(idSeed = "exact-$idx") }
        server.respondJson(
            "GET",
            "/api/v1/me/attempts",
            200,
            HistoryTestFixtures.encodePage(items, page = 0, size = 20, totalItems = 20, totalPages = 1),
        )
        val stack = createActiveStack()
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
            (memory.historyFeed.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 20
        }
        composeRule.onNodeWithTag(HistoryTestTags.LIST).performScrollToIndex(21)
        composeRule.waitForIdle()
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts"))
    }

    @Test
    fun deduplicatesOverlapStopsOnEmptySubsequentPageAndDropsStalePage1AfterFilterChange() {
        val dispatcher = StandardTestDispatcher()
        val testScope = TestScope(dispatcher)
        val page0Items = (1..20).map { HistoryTestFixtures.makeAttempt(idSeed = "dup-$it", examName = "Unfiltered $it") }
        val page1Overlap = listOf(page0Items.last(), HistoryTestFixtures.makeAttempt(idSeed = "dup-21", examName = "Unfiltered 21"))
        val partPage0 = (1..20).map { HistoryTestFixtures.makeAttempt(idSeed = "part-$it", examName = "PART $it", scope = "PART") }
        val partPage1 = listOf(HistoryTestFixtures.makeAttempt(idSeed = "part-21", examName = "PART 21", scope = "PART"))

        val heldUnfilteredPage1 = CompletableDeferred<ApiResult<PageResponseAttemptHistoryItem>>()
        val calls = mutableListOf<HistoryApiParams>()

        val feed = HistoryFeed(
            fetchHistory = { params ->
                calls.add(params)
                when {
                    params.scope == null && params.page == 0 -> ApiResult.Success(
                        PageResponseAttemptHistoryItem(page0Items, 0, 20, 45, 3),
                    )
                    params.scope == null && params.page == 1 -> heldUnfilteredPage1.await()
                    params.scope == "PART" && params.page == 0 -> ApiResult.Success(
                        PageResponseAttemptHistoryItem(partPage0, 0, 20, 21, 2),
                    )
                    params.scope == "PART" && params.page == 1 -> ApiResult.Success(
                        PageResponseAttemptHistoryItem(partPage1, 1, 20, 21, 2),
                    )
                    else -> ApiResult.Success(PageResponseAttemptHistoryItem(emptyList(), params.page, 20, 40, 2))
                }
            },
            catalogOptionsCache = CatalogOptionsCache(fetcher = { ApiResult.Success(emptyList()) }),
            connectivityMonitor = MutableConnectivityMonitor(initialOnline = true),
            scope = testScope,
        )

        feed.onAppear()
        testScope.runCurrent()
        feed.loadNextPage()
        testScope.runCurrent()

        feed.selectScope(HistoryScope.PART)
        testScope.runCurrent()

        heldUnfilteredPage1.complete(
            ApiResult.Success(PageResponseAttemptHistoryItem(page1Overlap, 1, 20, 45, 3)),
        )
        testScope.runCurrent()

        val afterRace = feed.state.value.loadState as LoadState.Loaded
        assertFalse("Dropped stale response must not set stale banner", afterRace.stale)
        assertEquals(20, afterRace.value.items.size)
        assertTrue(afterRace.value.items.all { it.scope == "PART" })

        feed.loadNextPage()
        testScope.runCurrent()
        assertEquals("PART", calls.last().scope)
        assertEquals(1, calls.last().page)
        val afterPartPage1 = feed.state.value.loadState as LoadState.Loaded
        assertEquals(21, afterPartPage1.value.items.size)

        val dedupCalls = mutableListOf<HistoryApiParams>()
        val dedupFeed = HistoryFeed(
            fetchHistory = { params ->
                dedupCalls.add(params)
                when (params.page) {
                    0 -> ApiResult.Success(PageResponseAttemptHistoryItem(page0Items, 0, 20, 45, 3))
                    1 -> ApiResult.Success(PageResponseAttemptHistoryItem(page1Overlap, 1, 20, 45, 3))
                    else -> ApiResult.Success(PageResponseAttemptHistoryItem(emptyList(), 2, 20, 45, 3))
                }
            },
            catalogOptionsCache = CatalogOptionsCache(fetcher = { ApiResult.Success(emptyList()) }),
            connectivityMonitor = MutableConnectivityMonitor(initialOnline = true),
            scope = testScope,
        )
        dedupFeed.onAppear()
        testScope.runCurrent()
        dedupFeed.loadNextPage()
        testScope.runCurrent()
        val afterDup = dedupFeed.state.value.loadState as LoadState.Loaded
        assertEquals("Duplicate attemptId must be removed", 21, afterDup.value.items.size)
        assertEquals(21, afterDup.value.items.map { it.attemptId }.distinct().size)

        dedupFeed.loadNextPage()
        testScope.runCurrent()
        val afterEmptyNext = dedupFeed.state.value.loadState as LoadState.Loaded
        assertEquals(21, afterEmptyNext.value.items.size)
        assertFalse(afterEmptyNext.value.hasMore)

        val callCountBeforeExtraScroll = dedupCalls.size
        dedupFeed.loadNextPage()
        testScope.runCurrent()
        assertEquals(callCountBeforeExtraScroll, dedupCalls.size)
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
