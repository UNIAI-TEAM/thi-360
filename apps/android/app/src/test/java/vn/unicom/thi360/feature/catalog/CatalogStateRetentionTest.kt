package vn.unicom.thi360.feature.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
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

            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("HSA")).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.kindChip("HSA")).performClick()
            composeRule.waitUntilIdle {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") >= 2 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EXAM-12")
            composeRule.waitUntilIdle {
                val state = feedStore.getOrCreate("acc-catalog-1", 0L, deps).state.value
                (state.loadState as? vn.unicom.thi360.core.ui.LoadState.Loaded)?.value?.items?.size == 24 &&
                    state.accessMap["EXAM-20"] is AccessCell.Ready
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EXAM-20")
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.productViewDetail("EXAM-20"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-20")).assertIsDisplayed()

            val productsCallsBeforeTrips = harness.server.requestCount("GET", "/api/v1/public/storefront/products")

            composeRule.onNodeWithTag(CatalogTestTags.productViewDetail("EXAM-20"))
                .assertIsDisplayed()
                .performClick()
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(ProductDetailTestTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ProductDetailTestTags.SCREEN).assertIsDisplayed()

            composeRule.onNodeWithTag(ProductDetailTestTags.BACK_BUTTON).performClick()
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.productCard("EXAM-20"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-20")).assertIsDisplayed()
            assertEquals(
                "Returning from detail must not re-fetch products",
                productsCallsBeforeTrips,
                harness.server.requestCount("GET", "/api/v1/public/storefront/products"),
            )

            composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.SCREEN).fetchSemanticsNodes().isEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.productCard("EXAM-20"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
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

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun defaultDependenciesRetainFilterAcrossDetailAndTabRoundtripsWithoutRefetchingProducts() {
        val app = RuntimeEnvironment.getApplication()
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            AppSessionContainer.setTestStack(app, harness.stack)
            try {
                val firstDefault = CatalogDependencies.defaultFor(app)
                val secondDefault = CatalogDependencies.defaultFor(app)
                assertSame(firstDefault.gateway, secondDefault.gateway)
                assertSame(firstDefault.feedStore, secondDefault.feedStore)

                composeRule.setContent {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }

                composeRule.waitUntilIdle {
                    composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("HSA")).fetchSemanticsNodes().isNotEmpty() &&
                        composeRule.onAllNodesWithTag(CatalogTestTags.productViewDetail("EXAM-DEMO-01"))
                            .fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(CatalogTestTags.kindChip("HSA")).performClick()
                composeRule.waitUntilIdle {
                    harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 2 &&
                        composeRule.onAllNodesWithTag(CatalogTestTags.ACTIVE_FILTERS_ROW)
                            .fetchSemanticsNodes().isNotEmpty() &&
                        composeRule.onAllNodesWithTag(CatalogTestTags.productViewDetail("EXAM-DEMO-01"))
                            .fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.waitForIdle()

                val productsCallsBeforeTrips = harness.server.requestCount("GET", "/api/v1/public/storefront/products")

                composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EXAM-DEMO-01")
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(CatalogTestTags.productHeader("EXAM-DEMO-01"))
                    .assertIsDisplayed()
                    .performClick()
                composeRule.waitUntilIdle {
                    composeRule.onAllNodesWithTag(ProductDetailTestTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(ProductDetailTestTags.BACK_BUTTON).performClick()
                composeRule.waitUntilIdle {
                    composeRule.onAllNodesWithTag(CatalogTestTags.ACTIVE_FILTERS_ROW)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(CatalogTestTags.ACTIVE_FILTERS_ROW).assertIsDisplayed()
                val afterDetailDeps = CatalogDependencies.defaultFor(app)
                assertEquals(
                    "HSA",
                    afterDetailDeps.feedStore.getOrCreate("acc-catalog-1", 0L, afterDetailDeps).state.value.query.kind,
                )
                assertEquals(
                    "Default dependencies must not re-fetch products after detail roundtrip",
                    productsCallsBeforeTrips,
                    harness.server.requestCount("GET", "/api/v1/public/storefront/products"),
                )

                composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
                composeRule.waitUntilIdle {
                    composeRule.onAllNodesWithTag(CatalogTestTags.SCREEN).fetchSemanticsNodes().isEmpty()
                }
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
                composeRule.waitUntilIdle {
                    composeRule.onAllNodesWithTag(CatalogTestTags.ACTIVE_FILTERS_ROW)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(CatalogTestTags.ACTIVE_FILTERS_ROW).assertIsDisplayed()
                val afterTabDeps = CatalogDependencies.defaultFor(app)
                assertEquals(
                    "HSA",
                    afterTabDeps.feedStore.getOrCreate("acc-catalog-1", 0L, afterTabDeps).state.value.query.kind,
                )
                assertEquals(
                    "Default dependencies must not re-fetch products after tab roundtrip",
                    productsCallsBeforeTrips,
                    harness.server.requestCount("GET", "/api/v1/public/storefront/products"),
                )
            } finally {
                AppSessionContainer.setTestStack(app, null)
            }
        }
    }
}
