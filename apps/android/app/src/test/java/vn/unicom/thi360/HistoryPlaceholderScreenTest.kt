package vn.unicom.thi360

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.history.HistoryTestTags
import vn.unicom.thi360.feature.myexams.MyExamsSessionMemory
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class HistoryPlaceholderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer
    private lateinit var app: Thi360Application

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
    @Config(qualifiers = "w412dp-h915dp")
    fun historyTabRendersRealVietnameseScreenWithoutPlaceholder() {
        server.respondIndexed("listHistory", 200, fixtureNameFilter = "attempts-list-200.json")
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

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History))
            .assertIsDisplayed()
            .assertTextEquals("Lịch sử luyện thi")
        composeRule.onNodeWithTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE).assertDoesNotExist()
        assertEquals(1, server.requestCount("GET", "/api/v1/me/attempts"))
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun historyTabRendersRealEnglishScreenWithoutPlaceholder() {
        server.respondIndexed("listHistory", 200, fixtureNameFilter = "attempts-list-200.json")
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
            runCatching {
                composeRule.onNodeWithTag(HistoryTestTags.COUNT_BADGE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History))
            .assertIsDisplayed()
            .assertTextEquals("Practice history")
        composeRule.onNodeWithTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE).assertDoesNotExist()
    }

    private fun createActiveStack(locale: AppLocale) = Thi360ApiFactory.createSessionStack(
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
        localeProvider = { locale },
    )
}
