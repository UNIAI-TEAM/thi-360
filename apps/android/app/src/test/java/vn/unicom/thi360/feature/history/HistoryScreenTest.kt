package vn.unicom.thi360.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicLong
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
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.myexams.MyExamsSessionMemory
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class HistoryScreenTest {

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
    fun firstOpen() {
        val toanAttempt = HistoryTestFixtures.makeAttempt(
            idSeed = "h-toan",
            examName = "Đề Toán 01",
            scope = "SUBJECT",
            subjectCode = "TOAN",
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts",
            200,
            HistoryTestFixtures.encodePage(listOf(toanAttempt), page = 0, totalItems = 7, totalPages = 1),
        )
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=EXAM", fixtureNameFilter = "exam")
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=SUBJECT", fixtureNameFilter = "subject")

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
                composeRule.onNodeWithText("Bài thi theo môn · Môn Toán học").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History))
            .assertTextEquals("Lịch sử luyện thi")
        composeRule.onNodeWithTag(HistoryTestTags.COUNT_BADGE).assertTextEquals("7 lượt")
        composeRule.onNodeWithTag(HistoryTestTags.SUBTITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE).assertDoesNotExist()

        val req = server.lastRequest("GET", "/api/v1/me/attempts")!!
        val url = req.requestUrl!!
        assertEquals("0", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("size"))
        assertNull(url.queryParameter("q"))
        assertNull(url.queryParameter("kind"))
        assertNull(url.queryParameter("scope"))
        assertNull(url.queryParameter("examId"))
        assertNull(url.queryParameter("from"))
        assertNull(url.queryParameter("to"))
        assertEquals(1, server.requestCount("GET", "/api/v1/catalog/options?group=EXAM"))
        assertEquals(1, server.requestCount("GET", "/api/v1/catalog/options?group=SUBJECT"))
    }

    @Test
    fun subjectCatalogFailureFallsBackToRawCodeAndStillRendersList() {
        val toanAttempt = HistoryTestFixtures.makeAttempt(
            idSeed = "h-fallback",
            examName = "Đề Toán fallback",
            scope = "SUBJECT",
            subjectCode = "TOAN",
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts",
            200,
            HistoryTestFixtures.encodePage(listOf(toanAttempt), page = 0, totalItems = 1, totalPages = 1),
        )
        server.respondJson("GET", "/api/v1/catalog/options?group=EXAM", 500, """{"code":"INTERNAL_ERROR"}""")
        server.respondJson("GET", "/api/v1/catalog/options?group=SUBJECT", 500, """{"code":"INTERNAL_ERROR"}""")

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
                composeRule.onNodeWithText("Bài thi theo môn · Môn TOAN").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun optionsNotRefetchedWithin300s() {
        val fakeClock = AtomicLong(10_000L)
        server.respondIndexed("listHistory", 200, fixtureNameFilter = "attempts-list-200.json")
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=EXAM", fixtureNameFilter = "exam")
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=SUBJECT", fixtureNameFilter = "subject")

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("access", "refresh", 300, 604800, false, "hv01", "acc-1"),
            ),
            localeProvider = { AppLocale.VI },
            catalogOptionsClockMillis = { fakeClock.get() },
        )
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
            server.requestCount("GET", "/api/v1/catalog/options?group=EXAM") == 1 &&
                server.requestCount("GET", "/api/v1/catalog/options?group=SUBJECT") == 1 &&
                server.requestCount("GET", "/api/v1/me/attempts") == 1
        }

        composeRule.onNodeWithTag(HistoryTestTags.SCOPE_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.scopeOption(HistoryScope.PART)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/me/attempts") == 2
        }

        fakeClock.addAndGet(299_000L)
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/me/attempts") == 3
        }

        assertEquals(1, server.requestCount("GET", "/api/v1/catalog/options?group=EXAM"))
        assertEquals(1, server.requestCount("GET", "/api/v1/catalog/options?group=SUBJECT"))
        assertEquals("PART", server.lastRequest("GET", "/api/v1/me/attempts")?.requestUrl?.queryParameter("scope"))
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
