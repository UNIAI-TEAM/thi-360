package vn.unicom.thi360.feature.catalog

import android.os.Looper
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogStatesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun loadingSkeletonEmptyCatalogAndNoResultsStates() {
        val gate = CompletableDeferred<Unit>()
        val h1 = PortalTestHarness.create(initialTokens = CatalogTestFixtures.activeStudentTokens())
        val h2 = PortalTestHarness.create(initialTokens = CatalogTestFixtures.activeStudentTokens()) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondApiFixture("GET", "/api/v1/public/storefront/products", 200, "storefront-products-list-200-empty.json")
        }
        val h3 = PortalTestHarness.create(initialTokens = CatalogTestFixtures.activeStudentTokens()) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/products") { req ->
                val kind = req.requestUrl?.queryParameter("kind")
                if (kind == "QDA") {
                    ContractFakeServer.jsonResponse(200, CatalogTestFixtures.productsEmptyJson())
                } else {
                    ContractFakeServer.jsonResponse(200, CatalogTestFixtures.productsListJson())
                }
            }
        }

        try {
            val blockingGateway = object : CatalogGateway {
                override suspend fun getFilters(): ApiResult<StorefrontFiltersData> {
                    gate.await()
                    return ApiResult.NetworkFailure(RuntimeException("cancelled"))
                }

                override suspend fun listProducts(params: CatalogQueryParams): ApiResult<ProductPageData> {
                    gate.await()
                    return ApiResult.NetworkFailure(RuntimeException("cancelled"))
                }

                override suspend fun getProductDetail(code: String): CatalogDetailResult =
                    CatalogDetailResult.Success(ProductOutcome.NotFound)

                override suspend fun queryExamAccess(codes: List<String>): ApiResult<List<ExamAccessRow>> =
                    ApiResult.Success(emptyList())
            }
            val d1 = CatalogTestFixtures.createDependencies(h1).copy(gateway = blockingGateway)
            val d2 = CatalogTestFixtures.createDependencies(h2)
            val d3 = CatalogTestFixtures.createDependencies(h3)
            var activeDeps by mutableStateOf(d1)
            var scenarioKey by mutableStateOf(1)

            composeRule.setContent {
                key(scenarioKey) {
                    CompositionLocalProvider(LocalCatalogDependencies provides activeDeps) {
                        Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                    }
                }
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.LOADING_SKELETON).assertIsDisplayed()
            gate.complete(Unit)

            composeRule.runOnIdle {
                activeDeps = d2
                scenarioKey = 2
            }
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.EMPTY_CATALOG).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.EMPTY_CATALOG).assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithTag(CatalogTestTags.FILTER_BLOCK).fetchSemanticsNodes().isEmpty())

            composeRule.runOnIdle {
                activeDeps = d3
                scenarioKey = 3
            }
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("QDA")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.kindChip("QDA")).performClick()
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.NO_RESULTS).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.FILTER_BLOCK).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.NO_RESULTS).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.NO_RESULTS_CLEAR).assertIsDisplayed().performClick()
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).assertIsDisplayed()
        } finally {
            gate.complete(Unit)
            h1.close()
            h2.close()
            h3.close()
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun serverError429RetriesBothEndpointsAndOfflineKeepsLoadedCards() {
        val shouldFail = AtomicBoolean(true)
        val now = AtomicLong(10_000L)

        val h1 = PortalTestHarness.create(initialTokens = CatalogTestFixtures.activeStudentTokens()) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/filters") {
                if (shouldFail.get()) {
                    ContractFakeServer.jsonResponse(429, CatalogTestFixtures.rateLimited429Json())
                } else {
                    ContractFakeServer.jsonResponse(200, CatalogTestFixtures.filtersJson())
                }
            }
            respondDynamic("GET", "/api/v1/public/storefront/products") {
                if (shouldFail.get()) {
                    MockResponse().setResponseCode(500)
                } else {
                    ContractFakeServer.jsonResponse(200, CatalogTestFixtures.productsListJson())
                }
            }
        }
        val h2 = PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
            initialOnline = false,
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            injectNetworkError("GET", "/api/v1/public/storefront/products", count = 1)
        }

        try {
            val d1 = CatalogTestFixtures.createDependencies(h1, clockMillis = { now.get() })
            val d2 = CatalogTestFixtures.createDependencies(h2, clockMillis = { now.get() })
            var activeDeps by mutableStateOf(d1)
            var scenarioKey by mutableStateOf(1)

            composeRule.setContent {
                key(scenarioKey) {
                    CompositionLocalProvider(LocalCatalogDependencies provides activeDeps) {
                        Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                    }
                }
            }

            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.ERROR_STATE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.FILTER_BLOCK).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.ERROR_STATE).assertIsDisplayed()

            shouldFail.set(false)
            composeRule.onNodeWithTag(CatalogTestTags.ERROR_RETRY).performClick()
            composeRule.waitUntilIdle {
                h1.server.requestCount("GET", "/api/v1/public/storefront/filters") == 2 &&
                    h1.server.requestCount("GET", "/api/v1/public/storefront/products") == 2 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).assertIsDisplayed()

            h1.connectivityMonitor.setOnline(false)
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.OFFLINE_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.OFFLINE_BANNER).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).assertIsDisplayed()

            composeRule.runOnIdle {
                activeDeps = d2
                scenarioKey = 2
            }
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.ERROR_STATE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.ERROR_STATE).assertIsDisplayed()
            composeRule.onNodeWithText("Không có kết nối").assertIsDisplayed()

            h2.connectivityMonitor.setOnline(true)
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).assertIsDisplayed()
        } finally {
            h1.close()
            h2.close()
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun filtersOnlyFailureWhenProductsReturns200ShowsErrorAndRetriesBoth() {
        val failFilters = AtomicBoolean(true)
        PortalTestHarness.create(initialTokens = CatalogTestFixtures.activeStudentTokens()) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/filters") {
                if (failFilters.get()) {
                    MockResponse().setResponseCode(500)
                } else {
                    ContractFakeServer.jsonResponse(200, CatalogTestFixtures.filtersJson())
                }
            }
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }

            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(CatalogTestTags.ERROR_STATE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.ERROR_STATE).assertIsDisplayed()
            assertTrue(
                composeRule.onAllNodesWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).fetchSemanticsNodes().isEmpty(),
            )

            failFilters.set(false)
            composeRule.onNodeWithTag(CatalogTestTags.ERROR_RETRY).performClick()
            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/filters") == 2 &&
                    harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 2 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("HSA")).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.kindChip("HSA")).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.productCard("EXAM-DEMO-01")).assertIsDisplayed()
        }
    }
}
