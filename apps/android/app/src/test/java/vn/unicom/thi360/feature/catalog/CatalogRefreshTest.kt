package vn.unicom.thi360.feature.catalog

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogRefreshTest {

    private suspend fun awaitCondition(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("Condition timed out after ${timeoutMs}ms")
            }
            delay(15L)
        }
    }

    @Test
    fun accessOnReturn() = runBlocking {
        listOf(
            60 to listOf(48, 12),
            48 to listOf(48),
            50 to listOf(48, 2),
        ).forEach { (totalCount, expectedBatchSizes) ->
            val allCodes = (1..totalCount).map { "RET-${it.toString().padStart(3, '0')}" }
            val pagesNeeded = (totalCount + 11) / 12
            val recordedReturnBatches = CopyOnWriteArrayList<List<String>>()
            val recordingReturn = AtomicBoolean(false)

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
                            totalItems = totalCount.toLong(),
                            totalPages = pagesNeeded,
                        ),
                    )
                }
                respondDynamic("POST", "/api/v1/me/exam-access/query") { req ->
                    val reqCodes = CatalogTestFixtures.parseAccessRequestCodes(req.body.clone().readUtf8())
                    if (recordingReturn.get()) {
                        recordedReturnBatches.add(reqCodes)
                    }
                    ContractFakeServer.jsonResponse(200, CatalogTestFixtures.accessResponseForCodes(reqCodes))
                }
            }.use { harness ->
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                try {
                    val gateway = OpenApiCatalogGateway.create(harness.server.url("/"), harness.stack)
                    val controller = CatalogFeedController(
                        gateway = gateway,
                        sessionManager = harness.stack.sessionManager,
                        connectivityMonitor = harness.connectivityMonitor,
                        scope = scope,
                    )
                    controller.ensureInitialized()
                    awaitCondition {
                        val loaded = controller.state.value.loadState as? LoadState.Loaded
                        loaded != null && loaded.value.items.size == minOf(12, totalCount)
                    }
                    for (p in 1 until pagesNeeded) {
                        val targetCount = minOf((p + 1) * 12, totalCount)
                        controller.loadNextPage()
                        awaitCondition {
                            val loaded = controller.state.value.loadState as? LoadState.Loaded
                            loaded != null && loaded.value.items.size == targetCount
                        }
                    }

                    val loaded = controller.state.value.loadState as LoadState.Loaded
                    assertEquals(totalCount, loaded.value.items.size)

                    val productsBeforeReturn = harness.server.requestCount("GET", "/api/v1/public/storefront/products")
                    recordingReturn.set(true)
                    controller.refreshAccessOnReturn()
                    awaitCondition { recordedReturnBatches.size == expectedBatchSizes.size }

                    assertEquals(
                        "Returning to catalog must NOT re-fetch products for totalCount=$totalCount",
                        productsBeforeReturn,
                        harness.server.requestCount("GET", "/api/v1/public/storefront/products"),
                    )
                    assertEquals(
                        "Unexpected exam-access chunk sizes for totalCount=$totalCount",
                        expectedBatchSizes.sorted(),
                        recordedReturnBatches.map { it.size }.sorted(),
                    )
                    assertEquals(allCodes.toSet(), recordedReturnBatches.flatten().toSet())
                } finally {
                    scope.cancel()
                }
            }
        }
    }

    @Test
    fun pullToRefresh() = runBlocking {
        val all40 = (1..40).map { "PTR-${it.toString().padStart(2, '0')}" }
        val failNextRefresh = AtomicBoolean(false)

        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/products") { req ->
                if (failNextRefresh.get()) {
                    return@respondDynamic MockResponse().setResponseCode(500)
                }
                val page = req.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 0
                val chunk = all40.drop(page * 12).take(12)
                ContractFakeServer.jsonResponse(
                    200,
                    CatalogTestFixtures.productsPageFromCertifiedCard(
                        codes = chunk,
                        page = page,
                        totalItems = 40,
                        totalPages = 4,
                    ),
                )
            }
        }.use { harness ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val gateway = OpenApiCatalogGateway.create(harness.server.url("/"), harness.stack)
                val controller = CatalogFeedController(
                    gateway = gateway,
                    sessionManager = harness.stack.sessionManager,
                    connectivityMonitor = harness.connectivityMonitor,
                    scope = scope,
                )

                controller.ensureInitialized()
                awaitCondition { (controller.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 12 }
                controller.loadNextPage()
                awaitCondition { (controller.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 24 }
                controller.loadNextPage()
                awaitCondition { (controller.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 36 }

                failNextRefresh.set(true)
                controller.pullToRefresh()
                awaitCondition { controller.state.value.refreshFailed && !controller.state.value.isRefreshing }
                val afterFail = controller.state.value
                assertTrue(afterFail.refreshFailed)
                assertEquals(36, (afterFail.loadState as LoadState.Loaded).value.items.size)

                failNextRefresh.set(false)
                val filtersBefore = harness.server.requestCount("GET", "/api/v1/public/storefront/filters")
                val accessBefore = harness.server.requestCount("POST", "/api/v1/me/exam-access/query")

                controller.pullToRefresh()
                awaitCondition {
                    val state = controller.state.value
                    val loaded = state.loadState as? LoadState.Loaded
                    state.isRefreshing == false &&
                        loaded?.value?.items?.size == 12 &&
                        harness.server.requestCount("POST", "/api/v1/me/exam-access/query") > accessBefore
                }

                assertEquals(filtersBefore + 1, harness.server.requestCount("GET", "/api/v1/public/storefront/filters"))
                val lastProducts = harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.requestUrl!!
                assertEquals("0", lastProducts.queryParameter("page"))
                assertEquals("12", lastProducts.queryParameter("size"))

                val afterPtr = controller.state.value.loadState as LoadState.Loaded
                assertEquals(12, afterPtr.value.items.size)
                assertEquals(40L, afterPtr.value.total)
                assertEquals(1, afterPtr.value.nextPage)
                assertEquals(all40.take(12), afterPtr.value.items.map { it.code })
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun staleNextPageDropped() = runBlocking {
        val page2Gate = CompletableDeferred<Unit>()
        val page2Started = CompletableDeferred<Unit>()
        val all40 = (1..40).map { "STALE-${it.toString().padStart(2, '0')}" }

        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/products") { req ->
                val page = req.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 0
                val chunk = all40.drop(page * 12).take(12)
                ContractFakeServer.jsonResponse(
                    200,
                    CatalogTestFixtures.productsPageFromCertifiedCard(
                        codes = chunk,
                        page = page,
                        totalItems = 40,
                        totalPages = 4,
                    ),
                )
            }
        }.use { harness ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val realGateway = OpenApiCatalogGateway.create(harness.server.url("/"), harness.stack)
                val racingGateway = object : CatalogGateway by realGateway {
                    override suspend fun listProducts(params: CatalogQueryParams): ApiResult<ProductPageData> {
                        if (params.page == 2) {
                            page2Started.complete(Unit)
                            page2Gate.await()
                        }
                        return realGateway.listProducts(params)
                    }
                }
                val controller = CatalogFeedController(
                    gateway = racingGateway,
                    sessionManager = harness.stack.sessionManager,
                    connectivityMonitor = harness.connectivityMonitor,
                    scope = scope,
                )

                controller.ensureInitialized()
                awaitCondition { (controller.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 12 }
                controller.loadNextPage()
                awaitCondition { (controller.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 24 }

                controller.loadNextPage()
                page2Started.await()

                controller.pullToRefresh()
                awaitCondition {
                    val state = controller.state.value
                    val loaded = state.loadState as? LoadState.Loaded
                    state.isRefreshing == false && loaded?.value?.items?.size == 12
                }

                page2Gate.complete(Unit)
                delay(150L)

                val finalPage = (controller.state.value.loadState as LoadState.Loaded).value
                assertEquals(12, finalPage.items.size)
                assertEquals(1, finalPage.nextPage)
                assertEquals(all40.take(12), finalPage.items.map { it.code })
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun filtersOnlyFailureDuringPullToRefreshPreservesItemsAndSetsRefreshFailed() = runBlocking {
        val failFilters = AtomicBoolean(false)
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/filters") {
                if (failFilters.get()) {
                    MockResponse().setResponseCode(500)
                } else {
                    ContractFakeServer.jsonResponse(200, CatalogTestFixtures.filtersJson())
                }
            }
        }.use { harness ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val gateway = OpenApiCatalogGateway.create(harness.server.url("/"), harness.stack)
                val controller = CatalogFeedController(
                    gateway = gateway,
                    sessionManager = harness.stack.sessionManager,
                    connectivityMonitor = harness.connectivityMonitor,
                    scope = scope,
                )

                controller.ensureInitialized()
                awaitCondition { (controller.state.value.loadState as? LoadState.Loaded)?.value?.items?.size == 2 }

                failFilters.set(true)
                controller.pullToRefresh()
                awaitCondition { controller.state.value.refreshFailed && !controller.state.value.isRefreshing }

                val loaded = controller.state.value.loadState as LoadState.Loaded
                assertTrue(loaded.stale)
                assertEquals(2, loaded.value.items.size)
            } finally {
                scope.cancel()
            }
        }
    }
}
