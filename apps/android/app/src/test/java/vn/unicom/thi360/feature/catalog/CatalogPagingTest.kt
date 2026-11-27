package vn.unicom.thi360.feature.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogPagingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun loadsThreePagesDeduplicatesShiftedItemsAndStopsOnEmptyNextPage() {
        val all30 = (1..30).map { "PG-${it.toString().padStart(2, '0')}" }
        val h1 = PortalTestHarness.create(initialTokens = CatalogTestFixtures.activeStudentTokens()) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/products") { req ->
                val page = req.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 0
                val chunk = when (page) {
                    0 -> all30.subList(0, 12)
                    1 -> listOf("PG-12") + all30.subList(12, 23)
                    2 -> all30.subList(23, 30)
                    else -> emptyList()
                }
                ContractFakeServer.jsonResponse(
                    200,
                    CatalogTestFixtures.productsPageFromCertifiedCard(
                        codes = chunk,
                        page = page,
                        totalItems = 30,
                        totalPages = 3,
                    ),
                )
            }
        }
        val h2 = PortalTestHarness.create(initialTokens = CatalogTestFixtures.activeStudentTokens()) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/products") { req ->
                val page = req.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 0
                if (page == 0) {
                    ContractFakeServer.jsonResponse(
                        200,
                        CatalogTestFixtures.productsPageFromCertifiedCard(
                            codes = (1..12).map { "EMP-$it" },
                            page = 0,
                            totalItems = 24,
                            totalPages = 2,
                        ),
                    )
                } else {
                    ContractFakeServer.jsonResponse(200, CatalogTestFixtures.productsEmptyJson())
                }
            }
        }

        try {
            val store1 = CatalogFeedStore()
            val store2 = CatalogFeedStore()
            val d1 = CatalogTestFixtures.createDependencies(h1, feedStore = store1)
            val d2 = CatalogTestFixtures.createDependencies(h2, feedStore = store2)
            var activeDeps by mutableStateOf(d1)
            var scenarioKey by mutableStateOf(1)

            composeRule.setContent {
                key(scenarioKey) {
                    CompositionLocalProvider(LocalCatalogDependencies provides activeDeps) {
                        Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                    }
                }
            }

            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(CatalogTestTags.SHOWING_SUMMARY).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.SHOWING_SUMMARY)
                .assertTextContains("Hiển thị 1–12 trong 30 bài thi đang mở bán")

            val ctrl1 = store1.getOrCreate("acc-catalog-1", 0L, d1)
            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("PG-12")
            composeRule.waitUntil(5_000) {
                (ctrl1.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 23
            }

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("PG-23")
            composeRule.waitUntil(5_000) {
                (ctrl1.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 30
            }
            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("PG-30")
            composeRule.waitForIdle()

            val loaded = ctrl1.state.value.loadState as LoadState.Loaded
            assertEquals(30, loaded.value.items.size)
            assertEquals(30, loaded.value.items.map { it.code }.distinct().size)
            assertEquals(TailState.End, loaded.value.tail)

            ctrl1.loadNextPage()
            composeRule.waitForIdle()
            assertEquals(3, h1.server.requestCount("GET", "/api/v1/public/storefront/products"))

            composeRule.runOnIdle {
                activeDeps = d2
                scenarioKey = 2
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty() &&
                    (store2.getOrCreate("acc-catalog-1", 0L, d2).state.value.loadState as? LoadState.Loaded)
                        ?.value?.items?.size == 12
            }
            val ctrl2 = store2.getOrCreate("acc-catalog-1", 0L, d2)
            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EMP-12")
            composeRule.waitUntil(5_000) {
                (ctrl2.state.value.loadState as? LoadState.Loaded)?.value?.tail == TailState.End
            }
            val loaded2 = ctrl2.state.value.loadState as LoadState.Loaded
            assertEquals(12L, loaded2.value.total)
            assertEquals(TailState.End, loaded2.value.tail)
        } finally {
            h1.close()
            h2.close()
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun nextPageErrorRetriesSamePage() {
        val page1Calls = AtomicInteger(0)
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/products") { req ->
                val page = req.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 0
                when (page) {
                    0 -> ContractFakeServer.jsonResponse(
                        200,
                        CatalogTestFixtures.productsPageFromCertifiedCard(
                            codes = (1..12).map { "RT-${it.toString().padStart(2, '0')}" },
                            page = 0,
                            totalItems = 24,
                            totalPages = 2,
                        ),
                    )

                    1 -> if (page1Calls.incrementAndGet() == 1) {
                        ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR","message":"boom"}""")
                    } else {
                        ContractFakeServer.jsonResponse(
                            200,
                            CatalogTestFixtures.productsPageFromCertifiedCard(
                                codes = (13..24).map { "RT-${it.toString().padStart(2, '0')}" },
                                page = 1,
                                totalItems = 24,
                                totalPages = 2,
                            ),
                        )
                    }

                    else -> ContractFakeServer.jsonResponse(200, CatalogTestFixtures.productsEmptyJson())
                }
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
                composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("RT-12")
            composeRule.waitUntil(5_000) {
                val loaded = feedStore.getOrCreate("acc-catalog-1", 0L, deps).state.value.loadState as? LoadState.Loaded
                loaded?.value?.tail is TailState.Error
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.productCard("RT-12")).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("__tail__")
            composeRule.onNodeWithTag(CatalogTestTags.TAIL_ERROR).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.TAIL_RETRY).assertIsDisplayed().performClick()

            composeRule.waitUntil(5_000) {
                val loaded = feedStore.getOrCreate("acc-catalog-1", 0L, deps).state.value.loadState as? LoadState.Loaded
                loaded?.value?.items?.size == 24
            }
            composeRule.waitForIdle()

            val retryReq = harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!
            assertEquals("1", retryReq.requestUrl?.queryParameter("page"))
            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("RT-13")
            composeRule.onNodeWithTag(CatalogTestTags.productCard("RT-13")).assertIsDisplayed()
        }
    }
}
