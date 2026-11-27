package vn.unicom.thi360.feature.catalog

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.ui.ListDetailTestTags
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogTwoPaneTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun twoPanePlaceholderSelectionSwitchFilterKeepsDetailAndFullScreenCta() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson("GET", "/api/v1/public/storefront/filters", 200, CatalogTestFixtures.filtersWithPartsJson())
            respondDynamic("GET", "/api/v1/public/storefront/products") { req ->
                val kind = req.requestUrl?.queryParameter("kind")
                if (kind == "QDA") {
                    ContractFakeServer.jsonResponse(
                        200,
                        CatalogTestFixtures.productsPageFromCertifiedCard(
                            codes = listOf("BUNDLE-DEMO-01"),
                            page = 0,
                            totalItems = 1,
                            totalPages = 1,
                        ),
                    )
                } else {
                    ContractFakeServer.jsonResponse(200, CatalogTestFixtures.productsListJson())
                }
            }
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                Box(modifier = Modifier.size(width = 840.dp, height = 720.dp)) {
                    CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                        Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                    }
                }
            }
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.productViewDetail("EXAM-DEMO-01"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(ListDetailTestTags.TWO_COLUMN).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.EMPTY_DETAIL_PLACEHOLDER)
                .assertIsDisplayed()
                .assertTextContains("Chọn một bài thi để xem chi tiết.")

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EXAM-DEMO-01")
            composeRule.onNodeWithTag(CatalogTestTags.productHeader("EXAM-DEMO-01")).performClick()
            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.PRODUCT_CODE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ListDetailTestTags.DETAIL_PANE).assertIsDisplayed()
            composeRule.onNodeWithTag(ProductDetailTestTags.PRODUCT_CODE)
                .assertTextContains("EXAM-DEMO-01", substring = true)

            composeRule.onNodeWithTag(CatalogTestTags.kindChip("QDA")).performClick()
            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 2 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.productCard("BUNDLE-DEMO-01"))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ProductDetailTestTags.PRODUCT_CODE)
                .assertTextContains("EXAM-DEMO-01", substring = true)

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("BUNDLE-DEMO-01")
            composeRule.onNodeWithTag(CatalogTestTags.productHeader("BUNDLE-DEMO-01")).performClick()
            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.BUNDLE_BUY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_PRICE_PANEL).performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_BUY_BUTTON).performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_SCREEN).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun collapsesFromWideToCompactPreservingFilterAndReturningToListCleanly() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            val feedStore = CatalogFeedStore()
            val deps = CatalogTestFixtures.createDependencies(harness, feedStore = feedStore)
            var widthDp by mutableStateOf(1280.dp)

            composeRule.setContent {
                Box(modifier = Modifier.size(width = widthDp, height = 800.dp)) {
                    CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                        Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                    }
                }
            }
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("HSA")).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.productViewDetail("EXAM-DEMO-01"))
                        .fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithTag(ListDetailTestTags.TWO_COLUMN).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.kindChip("HSA")).performClick()
            composeRule.waitUntilIdle {
                val state = feedStore.getOrCreate("acc-catalog-1", 0L, deps).state.value
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 2 &&
                    state.query.kind == "HSA" &&
                    state.accessMap["EXAM-DEMO-01"] is AccessCell.Ready &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.productViewDetail("EXAM-DEMO-01"))
                        .fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EXAM-DEMO-01")
            composeRule.onNodeWithTag(CatalogTestTags.productHeader("EXAM-DEMO-01")).performClick()
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(ProductDetailTestTags.PRODUCT_CODE).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.runOnIdle { widthDp = 600.dp }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ListDetailTestTags.SINGLE_COLUMN).assertIsDisplayed()
            composeRule.onNodeWithTag(ProductDetailTestTags.BACK_BUTTON).assertIsDisplayed().performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.LIST).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.ACTIVE_FILTERS_ROW).assertIsDisplayed()
        }
    }
}
