package vn.unicom.thi360.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
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
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.myexams.MyExamsSessionMemory
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class HistoryAuthTest {

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
    fun unauthorized401RefreshesAndRetriesSeamlessly() {
        val item = HistoryTestFixtures.makeAttempt(idSeed = "h-auth", examName = "Lịch sử sau refresh")
        server.inject401("GET", "/api/v1/me/attempts", count = 1)
        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
        server.respondJson("GET", "/api/v1/me/attempts", 200, HistoryTestFixtures.encodePage(listOf(item)))

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
                composeRule.onNodeWithText("Lịch sử sau refresh").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(HistoryTestTags.ERROR_STATE).assertDoesNotExist()
        assertEquals(2, server.requestCount("GET", "/api/v1/me/attempts"))
        assertEquals(1, server.requestCount("POST", "/api/v1/auth/mobile/refresh"))
    }

    @Test
    fun forbidden403DoesNotAutoRetryAndRoutesToStudentOnlyNotice() {
        server.respondIndexed("listHistory", 403)
        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 403, "refresh-403-app-student-only.json")

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
                composeRule.onNodeWithText("Ứng dụng này dành cho học viên").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts"))
        assertEquals(1, server.requestCount("POST", "/api/v1/auth/mobile/refresh"))
    }

    @Test
    fun forbidden403RoutesToLoginWhenRefreshRejected() {
        server.respondIndexed("listHistory", 403)
        server.respondAuthFixture(
            "POST",
            "/api/v1/auth/mobile/refresh",
            401,
            "refresh-401-session-revoked.json",
        )

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
                composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts"))
    }

    private fun createActiveStack() = Thi360ApiFactory.createSessionStack(
        baseUrl = server.url("/"),
        environment = Environment.DEV,
        tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "expired-access",
                refreshToken = "valid-refresh",
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
