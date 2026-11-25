package vn.unicom.thi360

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import okhttp3.mockwebserver.MockWebServer
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

@RunWith(RobolectricTestRunner::class)
class PlaceholderScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun placeholderDestinationsShowVietnameseTitlesAndEmptyStateOffline() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens(
                    accessToken = "active-access",
                    refreshToken = "active-refresh",
                    accessExpiresIn = 300,
                    refreshExpiresIn = 604800,
                    mustChangePassword = false,
                    username = "student.demo",
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

        val expected = listOf(
            Destination.Home to "Trang chủ",
            Destination.MyExams to "Bài thi của tôi",
            Destination.History to "Lịch sử",
        )

        expected.forEach { (destination, expectedTitle) ->
            composeRule.onNodeWithTag(ShellTestTags.navItem(destination)).performClick()
            composeRule.onNodeWithTag(ShellTestTags.screenTitle(destination))
                .assertIsDisplayed()
                .assertTextEquals(expectedTitle)
            composeRule.onNodeWithTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE)
                .assertIsDisplayed()
                .assertTextEquals("Chưa có nội dung")
        }

        assertEquals(
            "Opening placeholder tabs must trigger zero business API requests",
            0,
            server.requestCount,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun placeholderDestinationsShowEnglishTitlesAndEmptyState() {
        composeRule.setContent {
            Thi360App(preferences = AppPreferences(locale = AppLocale.EN))
        }

        val expected = listOf(
            Destination.Home to "Home",
            Destination.MyExams to "My exams",
            Destination.History to "History",
        )

        expected.forEach { (destination, expectedTitle) ->
            composeRule.onNodeWithTag(ShellTestTags.navItem(destination)).performClick()
            composeRule.onNodeWithTag(ShellTestTags.screenTitle(destination))
                .assertIsDisplayed()
                .assertTextEquals(expectedTitle)
            composeRule.onNodeWithTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE)
                .assertIsDisplayed()
                .assertTextEquals("No content yet")
        }
    }
}
