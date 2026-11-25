package vn.unicom.thi360

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App

@RunWith(RobolectricTestRunner::class)
class PlaceholderScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun placeholderDestinationsShowVietnameseTitlesAndEmptyStateOffline() {
        composeRule.setContent {
            Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
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

        val hasRetrofit = runCatching { Class.forName("retrofit2.Retrofit") }.isSuccess
        val hasOkHttp = runCatching { Class.forName("okhttp3.OkHttpClient") }.isSuccess
        assertTrue("AND-1 placeholder shell must not depend on Retrofit", !hasRetrofit)
        assertTrue("AND-1 placeholder shell must not depend on OkHttp", !hasOkHttp)
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
