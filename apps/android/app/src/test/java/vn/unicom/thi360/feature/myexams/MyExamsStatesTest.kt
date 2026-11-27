package vn.unicom.thi360.feature.myexams

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.routes.LocalMyExamsClockOverride
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class MyExamsStatesTest {

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
    fun loadingAndRetryPolicy() {
        val firstRequestGate = CountDownLatch(1)
        val firstRequestArrived = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val okCards = listOf(MyExamsTestFixtures.makeCard(code = "OK-1", name = "Bài sau khi thử lại"))

        server.respondDynamic("GET", "/api/v1/me/exams") {
            val n = calls.incrementAndGet()
            if (n == 1) {
                firstRequestArrived.countDown()
                firstRequestGate.await(5, TimeUnit.SECONDS)
            }
            if (n <= 3) {
                ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR","message":"fail"}""")
            } else {
                ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(okCards))
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

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { firstRequestArrived.count == 0L }
        composeRule.onNodeWithTag(MyExamsTestTags.LOADING_SKELETON)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Đang tải")

        firstRequestGate.countDown()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.ERROR_STATE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        assertEquals(3, server.requestCount("GET", "/api/v1/me/exams"))
        composeRule.onNodeWithText("Không tải được danh sách bài thi").assertIsDisplayed()
        composeRule.onNodeWithText("Kiểm tra kết nối mạng rồi thử lại.").assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.ERROR_RETRY_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Bài sau khi thử lại").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(4, server.requestCount("GET", "/api/v1/me/exams"))
    }

    @Test
    fun client4xxOtherThan401DoesNotAutoRetry() {
        server.respondJson("GET", "/api/v1/me/exams", 400, """{"code":"BAD_REQUEST","message":"bad"}""")
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

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.ERROR_STATE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", "/api/v1/me/exams"))
        composeRule.onNodeWithText("Không tải được danh sách bài thi").assertIsDisplayed()
    }

    @Test
    fun staleAndOffline() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val loadedAtMillis = Instant.parse("2026-11-20T02:05:00Z").toEpochMilli()
        val v1Cards = listOf(MyExamsTestFixtures.makeCard(code = "STALE-1", name = "Bài cũ"))
        val v2Cards = listOf(MyExamsTestFixtures.makeCard(code = "STALE-2", name = "Bài mới"))
        val retryGate = CountDownLatch(1)
        val retryArrived = CountDownLatch(1)
        val calls = AtomicInteger(0)

        server.respondDynamic("GET", "/api/v1/me/exams") {
            when (calls.incrementAndGet()) {
                1 -> ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(v1Cards))
                2 -> ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR","message":"fail"}""")
                else -> {
                    retryArrived.countDown()
                    retryGate.await(5, TimeUnit.SECONDS)
                    ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(v2Cards))
                }
            }
        }

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)

        composeRule.setContent {
            CompositionLocalProvider(LocalMyExamsClockOverride provides { loadedAtMillis }) {
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
                composeRule.onNodeWithText("Bài cũ").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.STALE_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithText("Bài cũ").assertIsDisplayed()
        composeRule.onNodeWithText("Dữ liệu chưa được cập nhật.").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Không tải lại được danh sách; đang hiện bản đã tải lúc 09:05.",
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.STALE_RETRY_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { retryArrived.count == 0L }
        composeRule.onNodeWithTag(MyExamsTestTags.STALE_RETRY_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(MyExamsTestTags.STALE_RETRY_BUTTON).performClick()
        assertEquals(3, server.requestCount("GET", "/api/v1/me/exams"))

        retryGate.countDown()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Bài mới").assertIsDisplayed()
                composeRule.onNodeWithTag(MyExamsTestTags.STALE_BANNER).assertDoesNotExist()
                true
            }.getOrDefault(false)
        }

        connectivity.setOnline(false)
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.STALE_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Bài mới").assertIsDisplayed()
    }

    @Test
    fun offlineWithoutDataShowsOfflineBlockAndStaleWithoutTimeUsesFallbackCopy() {
        connectivity.setOnline(false)
        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        val cards = listOf(MyExamsTestFixtures.makeCard(code = "OFF-1", name = "Bài sau khi có mạng"))
        server.respondJson("GET", "/api/v1/me/exams", 200, MyExamsTestFixtures.encodeResponse(cards))

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.OFFLINE_STATE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Không có kết nối").assertIsDisplayed()
        assertEquals(0, server.requestCount("GET", "/api/v1/me/exams"))

        connectivity.setOnline(true)
        composeRule.onNodeWithTag(MyExamsTestTags.OFFLINE_RETRY_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Bài sau khi có mạng").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun staleWithoutTimestampShowsNoTimeFallbackMessage() {
        val card = MyExamsTestFixtures.makeCard(code = "NOTIME-1", name = "Bài không rõ giờ")
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                MyExamsScreen(
                    feedState = MyExamsFeedState(
                        loadState = LoadState.Loaded(listOf(card), stale = true),
                        lastLoadedAtMillis = null,
                    ),
                    greetingName = "Nguyễn Văn An",
                    supportContact = SupportContact(),
                    showCompactBrandHeader = false,
                    onPullToRefresh = {},
                    onRetry = {},
                    onExploreExams = {},
                    onOpenOrder = {},
                    onOpenProduct = {},
                    onContinueAttempt = {},
                    onStartPrepare = {},
                    onOpenResult = {},
                    onOpenHistoryReset = {},
                    onOpenCheckout = {},
                )
            }
        }
        composeRule.onNodeWithText(
            "Không tải lại được danh sách; đang hiện bản đã tải trước đó.",
        ).assertIsDisplayed()
    }

    @Test
    fun empty() {
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

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.EMPTY_STATE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithText("Bạn chưa có bài thi nào").assertIsDisplayed()
        composeRule.onNodeWithText("Chọn bài thi phù hợp trong danh mục để bắt đầu luyện tập.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.EMPTY_EXPLORE_BUTTON)
            .assertTextContains("Khám phá bài thi")
            .performClick()

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
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
