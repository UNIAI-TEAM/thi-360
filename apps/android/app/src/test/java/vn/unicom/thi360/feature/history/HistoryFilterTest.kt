package vn.unicom.thi360.feature.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.util.TimeZone
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
class HistoryFilterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        app = ApplicationProvider.getApplicationContext()
        server = ContractFakeServer().start()
        app.setTestOverrides(connectivityMonitor = MutableConnectivityMonitor(initialOnline = true))
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
    fun kindScope() {
        val hsaItem = HistoryTestFixtures.makeAttempt(idSeed = "hsa-1", examName = "Đề HSA Chuẩn", examKindCode = "HSA")
        server.respondDynamic("GET", "/api/v1/me/attempts") {
            ContractFakeServer.jsonResponse(200, HistoryTestFixtures.encodePage(listOf(hsaItem)))
        }
        val examOptions = HistoryTestFixtures.loadExamCatalogOptions().take(2).mapIndexed { idx, opt ->
            if (idx == 0) {
                opt.copy(code = "HSA", nameVi = "Đánh giá năng lực", nameEn = "")
            } else {
                opt.copy(code = "TSA", nameVi = "", nameEn = "")
            }
        }
        server.respondJson(
            "GET",
            "/api/v1/catalog/options?group=EXAM",
            200,
            HistoryTestFixtures.encodeCatalogOptions(examOptions),
        )
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=SUBJECT", fixtureNameFilter = "subject")

        val stack = createActiveStack(AppLocale.EN)
        AppSessionContainer.setTestStack(app, stack)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.EN),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/catalog/options?group=EXAM") == 1 &&
                server.requestCount("GET", "/api/v1/me/attempts") == 1
        }

        composeRule.onNodeWithTag(HistoryTestTags.KIND_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.kindOption("HSA"))
            .assertIsDisplayed()
            .assertTextContains("Đánh giá năng lực")
        composeRule.onNodeWithTag(HistoryTestTags.kindOption("TSA"))
            .assertIsDisplayed()
            .assertTextContains("TSA")
        composeRule.onNodeWithTag(HistoryTestTags.kindOption("HSA")).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/me/attempts") == 2
        }
        val kindReq = server.lastRequest("GET", "/api/v1/me/attempts")!!.requestUrl!!
        assertEquals("HSA", kindReq.queryParameter("kind"))
        assertEquals("0", kindReq.queryParameter("page"))
        assertEquals("20", kindReq.queryParameter("size"))

        composeRule.onNodeWithTag(HistoryTestTags.SCOPE_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.scopeOption(HistoryScope.PART)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/me/attempts") == 3
        }
        assertEquals("PART", server.lastRequest("GET", "/api/v1/me/attempts")?.requestUrl?.queryParameter("scope"))

        composeRule.onNodeWithTag(HistoryTestTags.SCOPE_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.scopeOption(null)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/me/attempts") == 4
        }
        assertNull(server.lastRequest("GET", "/api/v1/me/attempts")?.requestUrl?.queryParameter("scope"))
    }

    @Test
    fun examCatalogFailureShowsOnlyAllExamsAndStillLoadsHistory() {
        server.respondIndexed("listHistory", 200, fixtureNameFilter = "attempts-list-200.json")
        server.respondJson("GET", "/api/v1/catalog/options?group=EXAM", 500, """{"code":"INTERNAL_ERROR"}""")
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=SUBJECT", fixtureNameFilter = "subject")

        val stack = createActiveStack(AppLocale.VI)
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
                composeRule.onNodeWithTag(HistoryTestTags.COUNT_BADGE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(HistoryTestTags.KIND_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.kindOption(null)).assertIsDisplayed()
        composeRule.onNodeWithTag(HistoryTestTags.kindOption("HSA")).assertDoesNotExist()
    }

    @Test
    fun dateRange() {
        val fromDate = LocalDate.of(2026, 11, 1)
        val toDate = LocalDate.of(2026, 11, 20)

        server.respondIndexed("listHistory", 200, fixtureNameFilter = "attempts-list-200.json")
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=EXAM", fixtureNameFilter = "exam")
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=SUBJECT", fixtureNameFilter = "subject")

        var activeStack = createActiveStack(AppLocale.VI)
        AppSessionContainer.setTestStack(app, activeStack)
        var tzEpoch by mutableIntStateOf(0)

        composeRule.setContent {
            key(tzEpoch) {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = activeStack.sessionManager,
                    validateOnLaunch = false,
                )
            }
        }

        for ((index, zoneId) in listOf("Pacific/Honolulu", "Pacific/Kiritimati").withIndex()) {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
            assertEquals(fromDate, HistoryRowFormat.utcMillisToLocalDate(HistoryRowFormat.localDateToUtcMillis(fromDate)))
            assertEquals(toDate, HistoryRowFormat.utcMillisToLocalDate(HistoryRowFormat.localDateToUtcMillis(toDate)))

            if (index > 0) {
                MyExamsSessionMemory.clearAllForTesting()
                server.respondIndexed("listHistory", 200, fixtureNameFilter = "attempts-list-200.json")
                server.resetRecordedRequests()
                activeStack = createActiveStack(AppLocale.VI)
                AppSessionContainer.setTestStack(app, activeStack)
                tzEpoch = index
                composeRule.waitForIdle()
            }

            val memory = MyExamsSessionMemory.forSession(
                sessionStack = activeStack,
                connectivityMonitor = app.connectivityMonitor,
            )

            composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                server.requestCount("GET", "/api/v1/me/attempts") == 1
            }

            composeRule.onNodeWithTag(HistoryTestTags.FROM_DATE_FIELD).performClick()
            composeRule.onNodeWithTag(HistoryTestTags.DATE_PICKER_DIALOG).assertIsDisplayed()
            composeRule.onNodeWithTag(HistoryTestTags.DATE_PICKER_CANCEL).performClick()

            composeRule.runOnIdle {
                memory.historyFeed.selectToDate(toDate)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                server.requestCount("GET", "/api/v1/me/attempts") == 2
            }
            composeRule.runOnIdle {
                memory.historyFeed.selectFromDate(LocalDate.of(2026, 11, 25))
            }
            composeRule.waitForIdle()
            assertEquals(2, server.requestCount("GET", "/api/v1/me/attempts"))
            assertNull(memory.historyFeed.state.value.filter.from)

            composeRule.runOnIdle {
                memory.historyFeed.selectFromDate(fromDate)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                server.requestCount("GET", "/api/v1/me/attempts") == 3
            }
            val rangeUrl = server.lastRequest("GET", "/api/v1/me/attempts")!!.requestUrl!!
            assertEquals("2026-11-01", rangeUrl.queryParameter("from"))
            assertEquals("2026-11-20", rangeUrl.queryParameter("to"))

            composeRule.onNodeWithTag(HistoryTestTags.FROM_DATE_CLEAR).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                server.requestCount("GET", "/api/v1/me/attempts") == 4
            }
            val afterClearFrom = server.lastRequest("GET", "/api/v1/me/attempts")!!.requestUrl!!
            assertNull(afterClearFrom.queryParameter("from"))
            assertEquals("2026-11-20", afterClearFrom.queryParameter("to"))

            server.respondIndexed("listHistory", 422)
            composeRule.onNodeWithTag(HistoryTestTags.TO_DATE_CLEAR).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(HistoryTestTags.STALE_BANNER).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
        }
    }

    @Test
    fun date422WithoutPriorDataShowsHistoryErrorBlock() {
        server.respondIndexed("listHistory", 422)
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=EXAM", fixtureNameFilter = "exam")
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=SUBJECT", fixtureNameFilter = "subject")

        val stack = createActiveStack(AppLocale.VI)
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
                composeRule.onNodeWithTag(HistoryTestTags.ERROR_STATE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts"))
    }

    @Test
    fun clearAndRetain() {
        val scored = HistoryTestFixtures.makeAttempt(idSeed = "retain-1", examName = "Đề Toán môn", scope = "SUBJECT")
        server.respondDynamic("GET", "/api/v1/me/attempts") {
            ContractFakeServer.jsonResponse(200, HistoryTestFixtures.encodePage(listOf(scored)))
        }
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=EXAM", fixtureNameFilter = "exam")
        server.respondIndexed("getOptions", 200, pathOverride = "/api/v1/catalog/options?group=SUBJECT", fixtureNameFilter = "subject")

        val stack = createActiveStack(AppLocale.VI)
        AppSessionContainer.setTestStack(app, stack)
        val memory = MyExamsSessionMemory.forSession(stack, app.connectivityMonitor)
        var capturedNavController: NavHostController? = null
        var configEpoch by mutableIntStateOf(0)

        composeRule.setContent {
            key(configEpoch) {
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
            server.requestCount("GET", "/api/v1/me/attempts") == 1
        }
        composeRule.onNodeWithTag(HistoryTestTags.CLEAR_FILTERS_BUTTON).assertDoesNotExist()

        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performTextInput("hsa")
        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performImeAction()
        composeRule.onNodeWithTag(HistoryTestTags.KIND_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.kindOption("HSA")).performClick()
        composeRule.runOnIdle { memory.historyFeed.selectFromDate(LocalDate.of(2026, 11, 1)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HistoryTestTags.CLEAR_FILTERS_BUTTON).assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            memory.historyFeed.state.value.filter.isEmpty
        }
        composeRule.onNodeWithTag(HistoryTestTags.CLEAR_FILTERS_BUTTON).assertDoesNotExist()
        val afterClear = server.lastRequest("GET", "/api/v1/me/attempts")!!.requestUrl!!
        assertNull(afterClear.queryParameter("q"))
        assertNull(afterClear.queryParameter("kind"))
        assertNull(afterClear.queryParameter("scope"))
        assertNull(afterClear.queryParameter("from"))

        composeRule.onNodeWithTag(HistoryTestTags.SCOPE_DROPDOWN).performClick()
        composeRule.onNodeWithTag(HistoryTestTags.scopeOption(HistoryScope.SUBJECT)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.lastRequest("GET", "/api/v1/me/attempts")?.requestUrl?.queryParameter("scope") == "SUBJECT"
        }

        val countBeforeResult = server.requestCount("GET", "/api/v1/me/attempts")
        composeRule.onNodeWithTag(HistoryTestTags.rowViewResultButton(scored.attemptId.toString()))
            .performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { checkNotNull(capturedNavController).popBackStack() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", "/api/v1/me/attempts") > countBeforeResult
        }
        assertEquals(HistoryScope.SUBJECT, memory.historyFeed.state.value.filter.scope)
        assertEquals("SUBJECT", server.lastRequest("GET", "/api/v1/me/attempts")?.requestUrl?.queryParameter("scope"))

        configEpoch = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.waitForIdle()
        assertEquals(HistoryScope.SUBJECT, memory.historyFeed.state.value.filter.scope)
        composeRule.onNodeWithTag(HistoryTestTags.SCOPE_DROPDOWN)
            .assertIsDisplayed()
            .assertTextContains("Bài thi theo môn")
    }

    private fun createActiveStack(locale: AppLocale) = Thi360ApiFactory.createSessionStack(
        baseUrl = server.url("/"),
        environment = Environment.DEV,
        tokenStore = InMemoryTokenStore(
            Tokens("access", "refresh", 300, 604800, false, "hv01", "acc-1"),
        ),
        localeProvider = { locale },
    )
}
