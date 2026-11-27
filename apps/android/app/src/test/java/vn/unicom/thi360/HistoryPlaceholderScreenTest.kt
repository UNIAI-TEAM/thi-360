package vn.unicom.thi360

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class HistoryPlaceholderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun historyPlaceholderShowsVietnameseTitleAndEmptyStateOffline() {
        val stack = Thi360ApiFactory.createSessionStack(
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
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History))
            .assertIsDisplayed()
            .assertTextEquals("Lịch sử")
        composeRule.onNodeWithTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE)
            .assertIsDisplayed()
            .assertTextEquals("Chưa có nội dung")

        assertEquals(
            "Opening History placeholder tab must trigger zero business API requests",
            0,
            server.totalRequestCount,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun historyPlaceholderShowsEnglishTitleAndEmptyState() {
        composeRule.setContent {
            Thi360App(preferences = AppPreferences(locale = AppLocale.EN))
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History))
            .assertIsDisplayed()
            .assertTextEquals("History")
        composeRule.onNodeWithTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE)
            .assertIsDisplayed()
            .assertTextEquals("No content yet")
    }
}
