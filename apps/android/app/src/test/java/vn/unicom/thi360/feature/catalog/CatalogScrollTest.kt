package vn.unicom.thi360.feature.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun scrollsSixtyCardsWithStableCodeKeysAndNoDuplicatePageRequests() {
        val all60 = (1..60).map { "SCR-${it.toString().padStart(2, '0')}" }
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/products") { req ->
                val page = req.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 0
                val chunk = all60.drop(page * 12).take(12)
                ContractFakeServer.jsonResponse(
                    200,
                    CatalogTestFixtures.productsPageFromCertifiedCard(
                        codes = chunk,
                        page = page,
                        totalItems = 60,
                        totalPages = 5,
                    ),
                )
            }
        }.use { harness ->
            val feedStore = CatalogFeedStore()
            val deps = CatalogTestFixtures.createDependencies(harness, feedStore = feedStore)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }

            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
            val ctrl = feedStore.getOrCreate("acc-catalog-1", 0L, deps)
            for (pageIndex in 0 until 5) {
                val expectedCount = (pageIndex + 1) * 12
                composeRule.waitUntilIdle {
                    (ctrl.state.value.loadState as? vn.unicom.thi360.core.ui.LoadState.Loaded)
                        ?.value?.items?.size == expectedCount
                }
                composeRule.waitForIdle()
                val lastCodeOnPage = all60[expectedCount - 1]
                composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey(lastCodeOnPage)
                composeRule.waitForIdle()
            }

            composeRule.onNodeWithTag(CatalogTestTags.productCard("SCR-60")).assertIsDisplayed()

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("SCR-01")
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("SCR-60")
            composeRule.waitForIdle()

            val productReqs = harness.server.requests("GET", "/api/v1/public/storefront/products")
            val requestedPages = productReqs.mapNotNull { it.requestUrl?.queryParameter("page")?.toIntOrNull() }
            assertEquals(listOf(0, 1, 2, 3, 4), requestedPages)
        }
    }
}
