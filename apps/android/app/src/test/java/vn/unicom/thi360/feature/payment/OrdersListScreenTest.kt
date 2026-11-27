package vn.unicom.thi360.feature.payment

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.StaleBannerTestTags
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.OrdersScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.OrdersViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class OrdersListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun displaysRowsBundleBadgeAmountsStatesAndActions() {
        var openedOrder: String? = null
        var openedMyExams = false
        val mixedOrders = PaymentTestFixtures.mutateJson("orders-list-200.json") { obj ->
            val template = obj["items"]!!.jsonArray.first().jsonObject
            val pendingRow = JsonObject(
                template + mapOf(
                    "id" to JsonPrimitive("00000000-0000-7000-8000-000000000001"),
                    "code" to JsonPrimitive("DH-PENDING-01"),
                    "productType" to JsonPrimitive("BUNDLE"),
                    "productName" to JsonPrimitive("Bộ đề HSA 2026"),
                    "state" to JsonPrimitive("PENDING_PAYMENT"),
                    "amountDueVnd" to JsonPrimitive(250_000L),
                    "expiresAt" to JsonPrimitive("2026-11-20T10:30:00Z"),
                ),
            )
            val paidRow = JsonObject(
                template + mapOf(
                    "id" to JsonPrimitive("00000000-0000-7000-8000-000000000002"),
                    "code" to JsonPrimitive("DH-PAID-02"),
                    "productType" to JsonPrimitive("EXAM"),
                    "productName" to JsonPrimitive("Đề Toán 02"),
                    "state" to JsonPrimitive("PAID"),
                    "paymentMethod" to JsonPrimitive("FREE"),
                    "amountDueVnd" to JsonPrimitive(0L),
                    "voucherDiscountVnd" to JsonPrimitive(200_000L),
                ),
            )
            val fulfilledRow = JsonObject(
                template + mapOf(
                    "id" to JsonPrimitive("00000000-0000-7000-8000-000000000003"),
                    "code" to JsonPrimitive("DH-DONE-03"),
                    "productType" to JsonPrimitive("EXAM"),
                    "productName" to JsonPrimitive("Đề Miễn Phí 03"),
                    "state" to JsonPrimitive("FULFILLED"),
                    "paymentMethod" to JsonPrimitive("FREE"),
                    "amountDueVnd" to JsonPrimitive(0L),
                    "voucherDiscountVnd" to JsonPrimitive(0L),
                ),
            )
            JsonObject(
                obj + mapOf(
                    "totalItems" to JsonPrimitive(3),
                    "totalPages" to JsonPrimitive(1),
                    "items" to JsonArray(listOf(pendingRow, paidRow, fulfilledRow)),
                ),
            )
        }
        val harness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders", 200, mixedOrders)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
        )
        try {
            vm.loadInitial()
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrdersScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenOrder = { openedOrder = it },
                        onOpenMyExams = { openedMyExams = true },
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.orderRow("DH-PENDING-01")).fetchSemanticsNodes().isNotEmpty()
            }

            val reqUrl = harness.server.lastRequest("GET", "/api/v1/me/orders")!!.requestUrl!!
            assertEquals("0", reqUrl.queryParameter("page"))
            assertEquals("20", reqUrl.queryParameter("size"))

            composeRule.onNodeWithText("Đơn của tôi").assertIsDisplayed()
            composeRule.onNodeWithText("student.demo", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Bộ đề").assertIsDisplayed()
            composeRule.onNodeWithText("Giữ giá tới 17:30").assertIsDisplayed()
            composeRule.onNodeWithText("250.000 đ").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.orderContinueButton("DH-PENDING-01")).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.orderCancelButton("DH-PENDING-01")).assertIsDisplayed()

            composeRule.onNodeWithText("Đã nhận tiền, đang cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithText("0 đ (đã áp dụng voucher)").assertIsDisplayed()
            composeRule.onNodeWithText("Thông tin liên hệ đang được cập nhật.", substring = true).assertIsDisplayed()

            composeRule.onNodeWithTag(PaymentTestTags.orderRow("DH-PENDING-01")).performClick()
            assertEquals("DH-PENDING-01", openedOrder)

            composeRule.onNodeWithTag(PaymentTestTags.orderGoMyExamsButton("DH-DONE-03")).performClick()
            assertTrue(openedMyExams)
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun emptyStateOpensCatalog() {
        var openedCatalog = false
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders", 200, "orders-list-200-empty.json")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
        )
        try {
            vm.loadInitial()
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrdersScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenOrder = {},
                        onOpenMyExams = {},
                        onOpenCheckout = {},
                        onOpenCatalog = { openedCatalog = true },
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDERS_EMPTY_STATE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Bạn chưa có đơn nào").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.ORDERS_EXPLORE_BUTTON).performClick()
            assertTrue(openedCatalog)
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun paginationAppendsNextPageAndStopsOnFinalPage() {
        val page0 = PaymentTestFixtures.mutateJson("orders-list-200.json") { obj ->
            val template = obj["items"]!!.jsonArray.first().jsonObject
            JsonObject(
                obj + mapOf(
                    "page" to JsonPrimitive(0),
                    "totalItems" to JsonPrimitive(2),
                    "totalPages" to JsonPrimitive(2),
                    "items" to JsonArray(listOf(JsonObject(template + ("code" to JsonPrimitive("DH-PAGE-0"))))),
                ),
            )
        }
        val page1 = PaymentTestFixtures.mutateJson("orders-list-200.json") { obj ->
            val template = obj["items"]!!.jsonArray.first().jsonObject
            JsonObject(
                obj + mapOf(
                    "page" to JsonPrimitive(1),
                    "totalItems" to JsonPrimitive(2),
                    "totalPages" to JsonPrimitive(2),
                    "items" to JsonArray(
                        listOf(
                            JsonObject(
                                template + mapOf(
                                    "id" to JsonPrimitive("00000000-0000-7000-8000-000000000099"),
                                    "code" to JsonPrimitive("DH-PAGE-1"),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        val harness = PaymentTestFixtures.createHarness {
            respondDynamic("GET", "/api/v1/me/orders") { req ->
                val pageParam = req.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 0
                if (pageParam == 0) ContractFakeServer.jsonResponse(200, page0)
                else ContractFakeServer.jsonResponse(200, page1)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
        )
        try {
            vm.loadInitial()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("GET", "/api/v1/me/orders") == 1 &&
                    vm.state.value.orders is vn.unicom.thi360.core.ui.LoadState.Loaded
            }
            vm.loadNextPage()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("GET", "/api/v1/me/orders") == 2 &&
                    !vm.state.value.isLoadingMore
            }
            // Final page reached (page + 1 == totalPages) -> calling loadNextPage does NOT issue another request
            vm.loadNextPage()
            composeRule.waitForIdle()
            assertEquals(2, harness.server.requestCount("GET", "/api/v1/me/orders"))
            val loaded = (vm.state.value.orders as vn.unicom.thi360.core.ui.LoadState.Loaded).value
            assertEquals(listOf("DH-PAGE-0", "DH-PAGE-1"), loaded.map { it.code })
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun initialErrorStaleBannerAndResumeReload() {
        val harness = PaymentTestFixtures.createHarness {
            respondSequence(
                "GET",
                "/api/v1/me/orders",
                { MockResponse().setResponseCode(500).setBody("""{"code":"INTERNAL_ERROR","message":"boom"}""") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-list-200.json") },
                { MockResponse().setResponseCode(503).setBody("""{"code":"SERVICE_UNAVAILABLE","message":"down"}""") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-list-200.json") },
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val fakeLifecycle = FakeLifecycleOwner().apply {
            registry.currentState = Lifecycle.State.RESUMED
        }
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
            clockMillis = { 1763632800000L }, // 2025-11-20T10:00:00Z -> 17:00 VN
        )
        try {
            vm.loadInitial()
            composeRule.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides fakeLifecycle) {
                    Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                        OrdersScreen(
                            viewModel = vm,
                            paymentBrowser = RecordingPaymentBrowser(),
                            supportContact = SupportContact(),
                            onBack = {},
                            onOpenOrder = {},
                            onOpenMyExams = {},
                            onOpenCheckout = {},
                            onOpenCatalog = {},
                            onSwitchAccount = {},
                        )
                    }
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDERS_ERROR_STATE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Không tải được danh sách đơn").assertIsDisplayed()
            composeRule.onNodeWithText("Thử lại").performClick()

            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.orderRow(PaymentTestFixtures.ORDER_CODE)).fetchSemanticsNodes().isNotEmpty()
            }

            // Refresh fails -> keeps existing data & shows stale banner with HH:mm
            vm.refresh()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(StaleBannerTestTags.BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Dữ liệu chưa được cập nhật.", substring = true).assertIsDisplayed()
            composeRule.onNodeWithTag(StaleBannerTestTags.BANNER).assertTextContains("17:00", substring = true)
            composeRule.onNodeWithTag(PaymentTestTags.orderRow(PaymentTestFixtures.ORDER_CODE)).assertIsDisplayed()

            // Returning from background triggers reload of page 0
            val countBeforeResume = harness.server.requestCount("GET", "/api/v1/me/orders")
            composeRule.runOnIdle {
                fakeLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                fakeLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("GET", "/api/v1/me/orders") > countBeforeResume &&
                    !vm.state.value.isRefreshing
            }
        } finally {
            scope.cancel()
            harness.close()
        }
    }
}
