package vn.unicom.thi360.feature.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import org.junit.Assert.assertEquals
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
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun firstOpenCallsBothEndpoints() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }

            composeRule.waitUntilIdle {
                harness.server.requestCount("GET", "/api/v1/public/storefront/filters") >= 1 &&
                    harness.server.requestCount("GET", "/api/v1/public/storefront/products") >= 1 &&
                    harness.server.requestCount("POST", "/api/v1/me/exam-access/query") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.SHOWING_SUMMARY).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            assertEquals(1, harness.server.requestCount("GET", "/api/v1/public/storefront/filters"))
            assertEquals(1, harness.server.requestCount("GET", "/api/v1/public/storefront/products"))

            val filtersReq = harness.server.lastRequest("GET", "/api/v1/public/storefront/filters")!!
            assertEquals("Bearer catalog-access-token", filtersReq.getHeader("Authorization"))

            val productsReq = harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!
            assertEquals("Bearer catalog-access-token", productsReq.getHeader("Authorization"))
            val query = productsReq.path.orEmpty()
            assertTrue("Expected page=0 in $query", query.contains("page=0"))
            assertTrue("Expected size=12 in $query", query.contains("size=12"))
            assertTrue("Expected sort=NEWEST in $query", query.contains("sort=NEWEST"))

            composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home))
                .assertIsDisplayed()
                .assertTextContains("Chọn bài thi")
            composeRule.onNodeWithTag(CatalogTestTags.SHOWING_SUMMARY)
                .assertIsDisplayed()
                .assertTextContains("Hiển thị 1–2 trong 2 bài thi đang mở bán")
            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("BUNDLE-DEMO-01")
            composeRule.onNodeWithTag(CatalogTestTags.productCard("BUNDLE-DEMO-01")).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun noLandingBlocks() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }

            composeRule.waitUntilIdle {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.SHOWING_SUMMARY).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            val forbiddenLandingTexts = listOf(
                "3 bước",
                "Câu hỏi thường gặp",
                "Đăng nhập để bắt đầu",
                "Hỗ trợ & liên hệ",
            )
            forbiddenLandingTexts.forEach { text ->
                assertTrue(
                    "Landing block text '$text' must not appear on Home catalog",
                    composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty(),
                )
            }
        }
    }
}
