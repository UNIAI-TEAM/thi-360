package vn.unicom.thi360

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.catalog.CatalogTestFixtures
import vn.unicom.thi360.feature.catalog.CatalogTestTags
import vn.unicom.thi360.feature.catalog.LocalCatalogDependencies
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class HomePlaceholderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun homeCatalogShowsVietnameseTitleAndProducts() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)

            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    AppRoot(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        onSelectLocale = {},
                        onSelectThemeMode = {},
                        sessionManager = harness.stack.sessionManager,
                        validateOnLaunch = false,
                    )
                }
            }

            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.SHOWING_SUMMARY).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
            composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home))
                .assertIsDisplayed()
                .assertTextEquals("Chọn bài thi")
            composeRule.onNodeWithTag(CatalogTestTags.SHOWING_SUMMARY)
                .assertIsDisplayed()
                .assertTextEquals("Hiển thị 1–2 trong 2 bài thi đang mở bán")

            assertTrue(harness.server.requestCount("GET", "/api/v1/public/storefront/filters") >= 1)
            assertTrue(harness.server.requestCount("GET", "/api/v1/public/storefront/products") >= 1)
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun homeCatalogShowsEnglishTitleAndProducts() {
        val deps = CatalogTestFixtures.createDeterministicFixtureDependencies()
        composeRule.setContent {
            CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                Thi360App(preferences = AppPreferences(locale = AppLocale.EN))
            }
        }
        composeRule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(CatalogTestTags.SHOWING_SUMMARY).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home))
            .assertIsDisplayed()
            .assertTextEquals("Choose an exam")
        composeRule.onNodeWithTag(CatalogTestTags.SHOWING_SUMMARY)
            .assertIsDisplayed()
            .assertTextEquals("Showing 1–2 of 2 exams on sale")
    }
}
