package vn.unicom.thi360.feature.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogStateRetentionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun preservesFilterAndScrollAcrossDetailRoundtripTabSwitchAndRotationWithoutReloadingProducts() {
        val allCodes = (1..24).map { "EXAM-${it.toString().padStart(2, '0')}" }
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/products") { req ->
                val page = req.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 0
                val chunk = allCodes.drop(page * 12).take(12)
                ContractFakeServer.jsonResponse(
                    200,
                    CatalogTestFixtures.productsPageFromCertifiedCard(
                        codes = chunk,
                        page = page,
                        totalItems = 24,
                        totalPages = 2,
                    ),
                )
            }
            respondPathPrefix("GET", "/api/v1/public/storefront/products/") {
                ContractFakeServer.jsonResponse(200, CatalogTestFixtures.examDetailJson())
            }
        }.use { harness ->
            val feedStore = CatalogFeedStore()
            val deps = CatalogTestFixtures.createDependencies(harness, feedStore = feedStore)

            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }

            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("HSA")).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.kindChip("HSA")).performClick()
            composeRule.waitUntil(5_000) {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") >= 2 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EXAM-12")
            composeRule.waitUntil(5_000) {
                (feedStore.getOrCreate("acc-catalog-1", 0L, deps).state.value.loadState as? vn.unicom.thi360.core.ui.LoadState.Loaded)
                    ?.value?.items?.size == 24
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EXAM-20")
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-20")).assertIsDisplayed()

            val productsCallsBeforeTrips = harness.server.requestCount("GET", "/api/v1/public/storefront/products")

            composeRule.onNodeWithTag(CatalogTestTags.productViewDetail("EXAM-20")).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ProductDetailTestTags.SCREEN).assertIsDisplayed()

            composeRule.onNodeWithTag(ProductDetailTestTags.BACK_BUTTON).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-20")).assertIsDisplayed()
            assertEquals(
                "Returning from detail must not re-fetch products",
                productsCallsBeforeTrips,
                harness.server.requestCount("GET", "/api/v1/public/storefront/products"),
            )

            composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-20")).assertIsDisplayed()
            assertEquals(
                "Switching tabs and returning must not re-fetch products",
                productsCallsBeforeTrips,
                harness.server.requestCount("GET", "/api/v1/public/storefront/products"),
            )

            RuntimeEnvironment.setQualifiers("w800dp-h412dp-land")
            composeRule.waitForIdle()
            assertEquals("HSA", feedStore.getOrCreate("acc-catalog-1", 0L, deps).state.value.query.kind)
            assertTrue(feedStore.getOrCreate("acc-catalog-1", 0L, deps).state.value.savedScrollIndex > 0)
            assertEquals(
                "Orientation change must not re-fetch products",
                productsCallsBeforeTrips,
                harness.server.requestCount("GET", "/api/v1/public/storefront/products"),
            )
        }
    }
}
